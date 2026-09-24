#define NOMINMAX
#include <windows.h>
#include <roapi.h>
#include <winstring.h>
#include <windows.media.h>
#include <windows.storage.streams.h>
#include <systemmediatransportcontrolsinterop.h>
#include <shlwapi.h>
#include <shcore.h>
#include <jni.h>
#include <jawt.h>
#include <jawt_md.h>
#include <atomic>
#include <memory>
#include <mutex>
#include <string>
#include <stdexcept>
#include <algorithm>
#include <type_traits>

#define JNI(name) Java_tv_hsrui_bolo_player_session_BoloDesktopSystemMediaNative_##name
namespace Media = ABI::Windows::Media;
namespace Foundation = ABI::Windows::Foundation;
namespace Streams = ABI::Windows::Storage::Streams;

static void check(HRESULT result) {
    if (FAILED(result)) throw std::runtime_error("SMTC HRESULT " + std::to_string(static_cast<unsigned long>(result)));
}
template<class T> class Com {
public:
    T *value = nullptr;
    ~Com() { if (value) value->Release(); }
    T **out() { return &value; }
    T *operator->() const { return value; }
    void reset() { if (value) value->Release(); value = nullptr; }
};
class String {
public:
    HSTRING value = nullptr;
    explicit String(const std::wstring &text) { check(WindowsCreateString(text.data(), static_cast<UINT32>(text.size()), &value)); }
    ~String() { WindowsDeleteString(value); }
    operator HSTRING() const { return value; }
};
static std::wstring string(JNIEnv *env, jstring value) {
    if (!value) return {};
    const jchar *chars = env->GetStringChars(value, nullptr);
    std::wstring result(reinterpret_cast<const wchar_t *>(chars), env->GetStringLength(value));
    env->ReleaseStringChars(value, chars);
    return result;
}
template<class T> static void activate(const wchar_t *name, Com<T> &result) {
    Com<IInspectable> instance;
    check(RoActivateInstance(String(name), instance.out()));
    check(instance->QueryInterface(__uuidof(T), reinterpret_cast<void **>(result.out())));
}

// 委托持有独立状态；注销后迟到的 COM 事件不能引用已释放的播放器或 JNI 全局引用。
struct EventState {
    std::mutex mutex;
    JavaVM *vm = nullptr;
    jobject target = nullptr;
    jmethodID callback = nullptr;
    jlong handle = 0;
    bool closed = false;
    std::wstring mediaId;
    void emit(int action, int64_t position = 0) {
        std::lock_guard<std::mutex> lock(mutex);
        if (closed || mediaId.empty()) return;
        JNIEnv *env = nullptr;
        bool attached = vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK;
        if (attached && vm->AttachCurrentThread(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) return;
        jstring media = env->NewString(reinterpret_cast<const jchar *>(mediaId.data()), static_cast<jsize>(mediaId.size()));
        env->CallVoidMethod(target, callback, handle, static_cast<jint>(action), media, static_cast<jlong>(position), 0.0);
        env->DeleteLocalRef(media);
        if (env->ExceptionCheck()) { env->ExceptionDescribe(); env->ExceptionClear(); }
        if (attached) vm->DetachCurrentThread();
    }
};
template<class EventClass, class Args> class Handler final : public Foundation::ITypedEventHandler<Media::SystemMediaTransportControls *, EventClass *> {
    using Interface = Foundation::ITypedEventHandler<Media::SystemMediaTransportControls *, EventClass *>;
    std::atomic<ULONG> references{1};
    std::shared_ptr<EventState> state;
public:
    explicit Handler(std::shared_ptr<EventState> state) : state(std::move(state)) { }
    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID id, void **object) override {
        if (!object) return E_POINTER;
        *object = nullptr;
        if (id == IID_IUnknown || id == __uuidof(Interface) || id == IID_IAgileObject) {
            *object = static_cast<Interface *>(this); AddRef(); return S_OK;
        }
        return E_NOINTERFACE;
    }
    ULONG STDMETHODCALLTYPE AddRef() override { return ++references; }
    ULONG STDMETHODCALLTYPE Release() override { auto count = --references; if (!count) delete this; return count; }
    HRESULT STDMETHODCALLTYPE Invoke(Media::ISystemMediaTransportControls *, Args *args) override {
        if (!args) return S_OK;
        if constexpr (std::is_same_v<Args, Media::ISystemMediaTransportControlsButtonPressedEventArgs>) {
            Media::SystemMediaTransportControlsButton button;
            if (SUCCEEDED(args->get_Button(&button))) switch (button) {
                case Media::SystemMediaTransportControlsButton_Play: state->emit(0); break;
                case Media::SystemMediaTransportControlsButton_Pause: state->emit(1); break;
                case Media::SystemMediaTransportControlsButton_Stop: state->emit(7); break;
                case Media::SystemMediaTransportControlsButton_Previous: state->emit(5); break;
                case Media::SystemMediaTransportControlsButton_Next: state->emit(6); break;
                default: break;
            }
        } else {
            Foundation::TimeSpan position{};
            if (SUCCEEDED(args->get_RequestedPlaybackPosition(&position))) state->emit(3, position.Duration / 10000);
        }
        return S_OK;
    }
};

struct BoloSystemMediaBridge {
    bool initialized = false, buttonBound = false, positionBound = false;
    EventRegistrationToken buttonToken{}, positionToken{};
    std::shared_ptr<EventState> state = std::make_shared<EventState>();
    Com<Media::ISystemMediaTransportControls> controls;
    Com<Media::ISystemMediaTransportControls2> controls2;
    Com<Media::ISystemMediaTransportControlsDisplayUpdater> display;
    void close(JNIEnv *env) {
        {
            std::lock_guard<std::mutex> lock(state->mutex);
            state->closed = true;
            if (state->target) env->DeleteGlobalRef(state->target);
            state->target = nullptr;
        }
        if (buttonBound) controls->remove_ButtonPressed(buttonToken);
        if (positionBound) controls2->remove_PlaybackPositionChangeRequested(positionToken);
        if (display.value) { display->ClearAll(); display->Update(); }
        if (controls.value) controls->put_IsEnabled(false);
        display.reset(); controls2.reset(); controls.reset();
        if (initialized) RoUninitialize();
    }
};

static HWND windowHandle(JNIEnv *env, jobject window) {
    if (!window) return nullptr;
    auto getAWT = reinterpret_cast<jboolean (JNICALL *)(JNIEnv *, JAWT *)>(GetProcAddress(GetModuleHandleW(L"jawt.dll"), "JAWT_GetAWT"));
    JAWT awt{};
    awt.version = JAWT_VERSION_1_4;
    if (!getAWT || !getAWT(env, &awt)) return nullptr;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, window);
    if (!surface) return nullptr;
    HWND result = nullptr;
    if (!(surface->Lock(surface) & JAWT_LOCK_ERROR)) {
        JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
        if (info) {
            result = static_cast<JAWT_Win32DrawingSurfaceInfo *>(info->platformInfo)->hwnd;
            surface->FreeDrawingSurfaceInfo(info);
        }
        surface->Unlock(surface);
    }
    awt.FreeDrawingSurface(surface);
    return result;
}
static void throwJava(JNIEnv *env, const std::exception &error) {
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) { env->ThrowNew(type, error.what()); env->DeleteLocalRef(type); }
}

extern "C" JNIEXPORT jlong JNICALL JNI(create)(JNIEnv *env, jobject self, jobject window) {
    auto bridge = std::make_unique<BoloSystemMediaBridge>();
    try {
        HWND hwnd = windowHandle(env, window);
        if (!hwnd) throw std::runtime_error("SMTC requires a live window HWND");
        // create/update/destroy 均由 Swing EDT 调用，同线程平衡 COM apartment。
        HRESULT initialized = RoInitialize(RO_INIT_SINGLETHREADED);
        if (initialized != RPC_E_CHANGED_MODE) check(initialized);
        bridge->initialized = SUCCEEDED(initialized);
        Com<ISystemMediaTransportControlsInterop> factory;
        check(RoGetActivationFactory(String(L"Windows.Media.SystemMediaTransportControls"), __uuidof(ISystemMediaTransportControlsInterop), reinterpret_cast<void **>(factory.out())));
        check(factory->GetForWindow(hwnd, __uuidof(Media::ISystemMediaTransportControls), reinterpret_cast<void **>(bridge->controls.out())));
        check(bridge->controls->QueryInterface(__uuidof(Media::ISystemMediaTransportControls2), reinterpret_cast<void **>(bridge->controls2.out())));
        check(bridge->controls->get_DisplayUpdater(bridge->display.out()));
        env->GetJavaVM(&bridge->state->vm);
        bridge->state->target = env->NewGlobalRef(self);
        jclass type = env->GetObjectClass(self);
        bridge->state->callback = env->GetMethodID(type, "onCommand", "(JILjava/lang/String;JD)V");
        env->DeleteLocalRef(type);
        if (!bridge->state->callback) throw std::runtime_error("SMTC callback unavailable");
        bridge->state->handle = reinterpret_cast<jlong>(bridge.get());
        auto buttons = new Handler<Media::SystemMediaTransportControlsButtonPressedEventArgs, Media::ISystemMediaTransportControlsButtonPressedEventArgs>(bridge->state);
        HRESULT result = bridge->controls->add_ButtonPressed(buttons, &bridge->buttonToken);
        buttons->Release(); check(result); bridge->buttonBound = true;
        auto positions = new Handler<Media::PlaybackPositionChangeRequestedEventArgs, Media::IPlaybackPositionChangeRequestedEventArgs>(bridge->state);
        result = bridge->controls2->add_PlaybackPositionChangeRequested(positions, &bridge->positionToken);
        positions->Release(); check(result); bridge->positionBound = true;
        return reinterpret_cast<jlong>(bridge.release());
    } catch (const std::exception &error) { bridge->close(env); if (!env->ExceptionCheck()) throwJava(env, error); return 0; }
}

extern "C" JNIEXPORT void JNICALL JNI(update)(JNIEnv *env, jobject, jlong handle, jstring mediaId,
    jstring title, jstring artist, jstring album, jbyteArray artwork, jboolean artworkChanged,
    jlong position, jlong duration, jdouble speed, jint status, jint commands) {
    auto bridge = reinterpret_cast<BoloSystemMediaBridge *>(handle);
    try {
        const auto media = string(env, mediaId);
        {
            std::lock_guard<std::mutex> lock(bridge->state->mutex);
            if (bridge->state->closed) return;
            bridge->state->mediaId = media;
        }
        check(bridge->controls->put_IsEnabled(!media.empty()));
        bridge->controls->put_IsPlayEnabled((commands & 1) != 0);
        bridge->controls->put_IsPauseEnabled((commands & 2) != 0);
        bridge->controls->put_IsStopEnabled((commands & 3) != 0);
        bridge->controls->put_IsPreviousEnabled((commands & 8) != 0);
        bridge->controls->put_IsNextEnabled((commands & 16) != 0);
        bridge->controls->put_PlaybackStatus(status == 1 ? Media::MediaPlaybackStatus_Playing :
            status == 2 ? Media::MediaPlaybackStatus_Paused : status == 3 ? Media::MediaPlaybackStatus_Changing : Media::MediaPlaybackStatus_Stopped);
        bridge->controls2->put_PlaybackRate(speed);
        bridge->display->put_Type(Media::MediaPlaybackType_Video);
        bridge->display->put_AppMediaId(String(media));
        Com<Media::IVideoDisplayProperties> video;
        check(bridge->display->get_VideoProperties(video.out()));
        video->put_Title(String(string(env, title)));
        auto subtitle = string(env, artist), collection = string(env, album);
        if (!collection.empty()) subtitle += (subtitle.empty() ? L"" : L" · ") + collection;
        video->put_Subtitle(String(subtitle));
        if (artworkChanged) {
            bridge->display->put_Thumbnail(nullptr);
            if (artwork) {
                jsize size = env->GetArrayLength(artwork);
                jbyte *bytes = env->GetByteArrayElements(artwork, nullptr);
                Com<IStream> memory;
                memory.value = SHCreateMemStream(reinterpret_cast<BYTE *>(bytes), static_cast<UINT>(size));
                env->ReleaseByteArrayElements(artwork, bytes, JNI_ABORT);
                if (memory.value) {
                    Com<Streams::IRandomAccessStream> stream;
                    check(CreateRandomAccessStreamOverStream(memory.value, BSOS_DEFAULT, __uuidof(Streams::IRandomAccessStream), reinterpret_cast<void **>(stream.out())));
                    Com<Streams::IRandomAccessStreamReferenceStatics> factory;
                    check(RoGetActivationFactory(String(L"Windows.Storage.Streams.RandomAccessStreamReference"), __uuidof(Streams::IRandomAccessStreamReferenceStatics), reinterpret_cast<void **>(factory.out())));
                    Com<Streams::IRandomAccessStreamReference> reference;
                    check(factory->CreateFromStream(stream.value, reference.out()));
                    check(bridge->display->put_Thumbnail(reference.value));
                }
            }
        }
        check(bridge->display->Update());
        Com<Media::ISystemMediaTransportControlsTimelineProperties> timeline;
        activate(L"Windows.Media.SystemMediaTransportControlsTimelineProperties", timeline);
        const int64_t end = std::max<int64_t>(duration, 0) * 10000;
        const int64_t current = std::min<int64_t>(std::max<int64_t>(position, 0) * 10000, end);
        timeline->put_StartTime({0}); timeline->put_EndTime({end});
        timeline->put_MinSeekTime({(commands & 4) ? 0 : current});
        timeline->put_MaxSeekTime({(commands & 4) ? end : current});
        timeline->put_Position({current});
        check(bridge->controls2->UpdateTimelineProperties(timeline.value));
    } catch (const std::exception &error) { throwJava(env, error); }
}

extern "C" JNIEXPORT void JNICALL JNI(destroy)(JNIEnv *env, jobject, jlong handle) {
    std::unique_ptr<BoloSystemMediaBridge> bridge(reinterpret_cast<BoloSystemMediaBridge *>(handle));
    bridge->close(env);
}
