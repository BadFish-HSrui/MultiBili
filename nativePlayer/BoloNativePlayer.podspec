Pod::Spec.new do |spec|
  spec.name = 'BoloNativePlayer'
  spec.version = '0.41.0'
  spec.summary = 'Bolo libmpv native player bridge.'
  spec.homepage = 'https://github.com/BadFish-HSrui/MultiBili'
  spec.license = { :type => 'LGPL-2.1-or-later', :text => File.read(File.join(__dir__, 'licenses/mpv__LICENSE.LGPL')) }
  spec.author = 'BadFish-HSrui'
  spec.source = { :git => 'https://github.com/BadFish-HSrui/MultiBili.git' }
  spec.ios.deployment_target = '16.0'
  spec.static_framework = true
  spec.vendored_frameworks = 'BoloNativePlayer.xcframework'
  spec.frameworks = 'UIKit', 'Foundation', 'AVFoundation', 'AudioToolbox', 'CoreAudio', 'CoreGraphics', 'CoreMedia', 'CoreVideo', 'VideoToolbox', 'OpenGLES', 'QuartzCore', 'Security', 'CoreFoundation'
  spec.libraries = 'c++', 'iconv', 'z', 'bz2'
  spec.resources = 'licenses/*'
end
