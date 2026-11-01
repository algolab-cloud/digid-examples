import Digid
import SwiftUI

/// The app's words in the language the app chose (English or Swahili), whatever the phone's
/// language is: they come from en.lproj or sw.lproj/Localizable.strings. The SDK's guidance
/// text is asked for in the same language. (An app that follows the phone's language can use
/// `NSLocalizedString` / `Text("key")` instead.)
struct Strings {
    let lang: Lang
    private let bundle: Bundle

    init(_ lang: Lang) {
        self.lang = lang
        let path = Bundle.main.path(forResource: lang.code, ofType: "lproj")
        bundle = path.flatMap(Bundle.init(path:)) ?? .main
    }

    /// The string for `key`, formatted with `args` (`%1$@` for text, `%1$ld` for numbers).
    func callAsFunction(_ key: String, _ args: CVarArg...) -> String {
        let format = bundle.localizedString(forKey: key, value: nil, table: nil)
        return args.isEmpty ? format : String(format: format, locale: Locale(identifier: lang.code), arguments: args)
    }
}

extension Lang {
    /// The language code of the app's .lproj folders.
    var code: String { self == .sw ? "sw" : "en" }

    /// The phone's language if it is Swahili, otherwise English.
    static var device: Lang { Locale.preferredLanguages.first?.hasPrefix("sw") == true ? .sw : .en }
}

private struct StringsKey: EnvironmentKey {
    static let defaultValue = Strings(.en)
}

extension EnvironmentValues {
    /// The app's words, in the language chosen on the start screen.
    var strings: Strings {
        get { self[StringsKey.self] }
        set { self[StringsKey.self] = newValue }
    }
}
