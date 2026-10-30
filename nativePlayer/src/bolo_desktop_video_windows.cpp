#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#include <d3d11.h>
#include <dxgi1_2.h>
#include <dcomp.h>
#include <GL/gl.h>
#include <GL/glext.h>
#include <GL/wglext.h>
#include <cstdio>
#include <cstring>
#include <memory>
#include <stdexcept>
#include <string>
#include "bolo_desktop_video.h"

namespace {
template<class T> class Com {
public:
    T *value = nullptr;
    Com() = default;
    Com(const Com &) = delete;
    Com &operator=(const Com &) = delete;
    ~Com() { reset(); }
    T **out() { return &value; }
    T *operator->() const { return value; }
    void reset() { if (value) value->Release(); value = nullptr; }
};

void check(HRESULT result, const char *stage) {
    if (FAILED(result)) {
        char message[192];
        std::snprintf(message, sizeof(message), "Windows video %s: HRESULT 0x%08lx", stage, static_cast<unsigned long>(result));
        throw std::runtime_error(message);
    }
}

void require(bool condition, const char *stage) {
    if (!condition) throw std::runtime_error(std::string("Windows video ") + stage);
}

template<class T> T proc(const char *name) {
    auto address = wglGetProcAddress(name);
    auto value = reinterpret_cast<intptr_t>(address);
    require(address && value != 1 && value != 2 && value != 3 && value != -1, name);
    return reinterpret_cast<T>(address);
}

void report(JNIEnv *env, const std::exception &error) {
    auto type = env->FindClass("java/lang/IllegalStateException");
    if (type) { env->ThrowNew(type, error.what()); env->DeleteLocalRef(type); }
}

// 所有 GL/D3D 操作由同一 renderer 上下文串行调用；不持有播放器或 Java 宿主引用。
class VideoOutput {
    HGLRC glContext = wglGetCurrentContext();
    HWND window;
    Com<ID3D11Device> device;
    Com<ID3D11DeviceContext> context;
    Com<IDXGISwapChain1> swap;
    Com<IDCompositionDevice> composition;
    Com<IDCompositionTarget> target;
    Com<IDCompositionVisual> visual;
    Com<ID3D11Texture2D> texture;
    HANDLE interopDevice = nullptr, interopObject = nullptr;
    GLuint glTexture = 0, framebuffer = 0;
    bool locked = false, shown = false;
    int width = 0, height = 0;
    uint64_t frames = 0;
    PFNWGLDXOPENDEVICENVPROC openDevice = nullptr;
    PFNWGLDXCLOSEDEVICENVPROC closeDevice = nullptr;
    PFNWGLDXREGISTEROBJECTNVPROC registerObject = nullptr;
    PFNWGLDXUNREGISTEROBJECTNVPROC unregisterObject = nullptr;
    PFNWGLDXLOCKOBJECTSNVPROC lockObjects = nullptr;
    PFNWGLDXUNLOCKOBJECTSNVPROC unlockObjects = nullptr;
    PFNGLGENFRAMEBUFFERSPROC genFramebuffers = nullptr;
    PFNGLBINDFRAMEBUFFERPROC bindFramebuffer = nullptr;
    PFNGLFRAMEBUFFERTEXTURE2DPROC framebufferTexture = nullptr;
    PFNGLCHECKFRAMEBUFFERSTATUSPROC framebufferStatus = nullptr;
    PFNGLDELETEFRAMEBUFFERSPROC deleteFramebuffers = nullptr;

    void current() {
        require(glContext && wglGetCurrentContext() == glContext, "GL context changed");
    }

    void releaseSurface() {
        if (locked) {
            require(unlockObjects(interopDevice, 1, &interopObject), "unlock during release failed");
            locked = false;
        }
        if (interopObject) {
            require(unregisterObject(interopDevice, interopObject), "unregister texture failed");
            interopObject = nullptr;
        }
        if (framebuffer) deleteFramebuffers(1, &framebuffer);
        if (glTexture) glDeleteTextures(1, &glTexture);
        framebuffer = glTexture = 0;
        texture.reset();
    }

    void resize(int w, int h) {
        require(w > 0 && h > 0 && w <= 16384 && h <= 16384, "invalid surface size");
        releaseSurface();
        if (swap.value) {
            check(swap->ResizeBuffers(2, w, h, DXGI_FORMAT_B8G8R8A8_UNORM, 0), "resize swapchain");
        } else {
            Com<IDXGIFactory2> factory;
            check(CreateDXGIFactory1(IID_PPV_ARGS(factory.out())), "DXGI factory");
            DXGI_SWAP_CHAIN_DESC1 desc{};
            desc.Width = w;
            desc.Height = h;
            desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
            desc.SampleDesc.Count = 1;
            desc.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
            desc.BufferCount = 2;
            desc.SwapEffect = DXGI_SWAP_EFFECT_FLIP_SEQUENTIAL;
            desc.AlphaMode = DXGI_ALPHA_MODE_PREMULTIPLIED;
            desc.Scaling = DXGI_SCALING_STRETCH;
            check(factory->CreateSwapChainForComposition(device.value, &desc, nullptr, swap.out()), "composition swapchain");
            check(visual->SetContent(swap.value), "visual content");
        }
        D3D11_TEXTURE2D_DESC desc{};
        desc.Width = w;
        desc.Height = h;
        desc.MipLevels = desc.ArraySize = desc.SampleDesc.Count = 1;
        desc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
        desc.Usage = D3D11_USAGE_DEFAULT;
        desc.BindFlags = D3D11_BIND_RENDER_TARGET | D3D11_BIND_SHADER_RESOURCE;
        check(device->CreateTexture2D(&desc, nullptr, texture.out()), "shared texture");
        glGenTextures(1, &glTexture);
        genFramebuffers(1, &framebuffer);
        interopObject = registerObject(interopDevice, texture.value, glTexture, GL_TEXTURE_2D, WGL_ACCESS_READ_WRITE_NV);
        require(interopObject, "register shared texture failed");
        require(lockObjects(interopDevice, 1, &interopObject), "lock shared texture failed");
        locked = true;
        GLint previousDraw = 0, previousRead = 0;
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &previousDraw);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &previousRead);
        bindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        framebufferTexture(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, glTexture, 0);
        auto status = framebufferStatus(GL_FRAMEBUFFER);
        bindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
        bindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
        require(unlockObjects(interopDevice, 1, &interopObject), "unlock shared texture failed");
        locked = false;
        require(status == GL_FRAMEBUFFER_COMPLETE, "shared framebuffer incomplete");
        width = w;
        height = h;
    }

public:
    explicit VideoOutput(HWND hwnd) : window(hwnd) { }

    void initialize() {
        current();
        DWORD process = 0;
        GetWindowThreadProcessId(window, &process);
        require(IsWindow(window) && process == GetCurrentProcessId(), "invalid composition host");
        auto extensions = proc<PFNWGLGETEXTENSIONSSTRINGARBPROC>("wglGetExtensionsStringARB")(wglGetCurrentDC());
        require(extensions && std::strstr(extensions, "WGL_NV_DX_interop2"), "WGL_NV_DX_interop2 unavailable");
        openDevice = proc<PFNWGLDXOPENDEVICENVPROC>("wglDXOpenDeviceNV");
        closeDevice = proc<PFNWGLDXCLOSEDEVICENVPROC>("wglDXCloseDeviceNV");
        registerObject = proc<PFNWGLDXREGISTEROBJECTNVPROC>("wglDXRegisterObjectNV");
        unregisterObject = proc<PFNWGLDXUNREGISTEROBJECTNVPROC>("wglDXUnregisterObjectNV");
        lockObjects = proc<PFNWGLDXLOCKOBJECTSNVPROC>("wglDXLockObjectsNV");
        unlockObjects = proc<PFNWGLDXUNLOCKOBJECTSNVPROC>("wglDXUnlockObjectsNV");
        genFramebuffers = proc<PFNGLGENFRAMEBUFFERSPROC>("glGenFramebuffers");
        bindFramebuffer = proc<PFNGLBINDFRAMEBUFFERPROC>("glBindFramebuffer");
        framebufferTexture = proc<PFNGLFRAMEBUFFERTEXTURE2DPROC>("glFramebufferTexture2D");
        framebufferStatus = proc<PFNGLCHECKFRAMEBUFFERSTATUSPROC>("glCheckFramebufferStatus");
        deleteFramebuffers = proc<PFNGLDELETEFRAMEBUFFERSPROC>("glDeleteFramebuffers");
        check(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, D3D11_CREATE_DEVICE_BGRA_SUPPORT,
            nullptr, 0, D3D11_SDK_VERSION, device.out(), nullptr, context.out()), "D3D11 device");
        Com<IDXGIDevice> dxgi;
        check(device->QueryInterface(IID_PPV_ARGS(dxgi.out())), "DXGI device");
        check(DCompositionCreateDevice(dxgi.value, IID_PPV_ARGS(composition.out())), "composition device");
        // 同一 HWND 的下层 target 承载视频，上层 target 继续由 Compose 管理。
        check(composition->CreateTargetForHwnd(window, FALSE, target.out()), "lower composition target");
        check(composition->CreateVisual(visual.out()), "video visual");
        interopDevice = openDevice(device.value);
        require(interopDevice, "open GPU interop device failed");
        std::printf("Bolo Windows video GPU interop: %s\n", glGetString(GL_RENDERER));
        std::fflush(stdout);
    }

    void update(int x, int y, int w, int h, bool visible, int left, int top, int right, int bottom) {
        current();
        require(IsWindow(window), "composition host destroyed");
        if (width != w || height != h) resize(w, h);
        check(visual->SetOffsetX(static_cast<float>(x)), "offset X");
        check(visual->SetOffsetY(static_cast<float>(y)), "offset Y");
        D2D_RECT_F clip{static_cast<float>(left), static_cast<float>(top), static_cast<float>(right), static_cast<float>(bottom)};
        check(visual->SetClip(clip), "clip");
        shown = visible;
        // 首帧完成前不暴露尚未初始化的交换链。
        check(target->SetRoot(shown && frames ? visual.value : nullptr), "visibility");
        check(composition->Commit(), "geometry commit");
    }

    int begin() {
        current();
        require(IsWindow(window), "composition host destroyed");
        require(!locked && interopObject, "invalid frame state");
        require(lockObjects(interopDevice, 1, &interopObject), "lock frame failed");
        locked = true;
        return static_cast<int>(framebuffer);
    }

    void end(bool present) {
        current();
        require(locked, "frame not locked");
        require(unlockObjects(interopDevice, 1, &interopObject), "unlock frame failed");
        locked = false;
        if (!present) return;
        Com<ID3D11Texture2D> buffer;
        check(swap->GetBuffer(0, IID_PPV_ARGS(buffer.out())), "backbuffer");
        context->CopyResource(buffer.value, texture.value);
        check(swap->Present(0, 0), "present");
        if (frames++ == 0 && shown) {
            check(target->SetRoot(visual.value), "first frame visibility");
            check(composition->Commit(), "first frame commit");
        }
    }

    ~VideoOutput() {
        // 调用者已停帧并释放 mpv render context；纹理必须在原 GL 上下文注销。
        try { releaseSurface(); } catch (const std::exception &error) { std::fprintf(stderr, "%s\n", error.what()); }
        if (interopDevice && closeDevice && !closeDevice(interopDevice))
            std::fprintf(stderr, "Bolo Windows video close interop failed\n");
        if (target.value) target->SetRoot(nullptr);
        if (composition.value) composition->Commit();
        if (context.value) { context->ClearState(); context->Flush(); }
        std::printf("Bolo Windows video closed: presented=%llu cpuReadbacks=0\n", static_cast<unsigned long long>(frames));
        std::fflush(stdout);
    }
};
}

extern "C" void *bolo_desktop_video_windows_create(JNIEnv *env, jlong hwnd) {
    try {
        auto output = std::make_unique<VideoOutput>(reinterpret_cast<HWND>(static_cast<intptr_t>(hwnd)));
        output->initialize();
        return output.release();
    } catch (const std::exception &error) { report(env, error); return nullptr; }
}
extern "C" void bolo_desktop_video_windows_update(JNIEnv *env, void *output, int x, int y, int w, int h, int visible,
    int left, int top, int right, int bottom) {
    try { static_cast<VideoOutput *>(output)->update(x, y, w, h, visible, left, top, right, bottom); }
    catch (const std::exception &error) { report(env, error); }
}
extern "C" int bolo_desktop_video_windows_begin(JNIEnv *env, void *output) {
    try { return static_cast<VideoOutput *>(output)->begin(); }
    catch (const std::exception &error) { report(env, error); return 0; }
}
extern "C" void bolo_desktop_video_windows_end(JNIEnv *env, void *output, int present) {
    try { static_cast<VideoOutput *>(output)->end(present); }
    catch (const std::exception &error) { report(env, error); }
}
extern "C" void bolo_desktop_video_windows_close(void *output) {
    delete static_cast<VideoOutput *>(output);
}
