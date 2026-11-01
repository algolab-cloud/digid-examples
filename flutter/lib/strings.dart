import 'package:digid/digid.dart';
import 'package:flutter/foundation.dart';

/// The app's language. The app owns it: its own EN / SW control sets it, and the open flow
/// follows it (the flow has no language control of its own).
final appLang = ValueNotifier<Lang>(Lang.en);

/// The English or Swahili text for the app's own screens. The flow's screens bring their own.
String tr(String en, String sw) => appLang.value == Lang.sw ? sw : en;
