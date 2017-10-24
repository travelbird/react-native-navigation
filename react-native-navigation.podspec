require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = "react-native-navigation"
  s.version      = package['version']
  s.summary      = "Native navigation library for React Native applications"

  s.authors      = { "intelligibabble" => "leland.m.richardson@gmail.com" }
  s.homepage     = "https://github.com/travelbird/react-native-navigation#readme"
  s.license      = package['license']
  s.platform     = :ios, "8.0"

  s.module_name  = 'ReactNativeNavigation'

  s.source       = { :git => "https://github.com/travelbird/react-native-navigation.git", :tag => "v#{s.version}" }
  s.source_files  = "lib/ios/react-native-navigation/*.{h,m,swift}"

  s.dependency 'React'
  s.frameworks = 'UIKit'
end
