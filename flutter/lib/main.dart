// digid Flutter example: a small host app around the SDK's drop-in flow.
//
// Home (the app's own screen) → DigidFlow.start (the SDK's screens) → the result the flow
// returned, on the app's side.
import 'package:digid/digid.dart';
import 'package:flutter/material.dart';

import 'home_page.dart';

/// The licence key, from `--dart-define=DIGID_LICENCE=digidlic1.…` or
/// `--dart-define-from-file=digid.local.json` (git-ignored). Never put a key in the code.
/// Empty runs the SDK in evaluation mode: everything works, results are watermarked and
/// never better than Review.
const digidLicence = String.fromEnvironment('DIGID_LICENCE');

/// The engine, created once per process at start-up: the models load off the UI isolate, and
/// the licence is applied here (it is checked on the phone, against this app's id).
final Future<Digid> engine = DigidFlow.createEngine(licence: digidLicence);

void main() => runApp(const ExampleApp());

class ExampleApp extends StatelessWidget {
  const ExampleApp({super.key});

  static const ube = Color(0xFF3F48FD);

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(seedColor: ube, primary: ube, surface: Colors.white);
    return MaterialApp(
      title: 'digid example',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: scheme,
        scaffoldBackgroundColor: Colors.white,
        // Inter ships with the plugin (the flow's default font), so the host matches the flow.
        fontFamily: 'packages/digid/Inter',
        filledButtonTheme: FilledButtonThemeData(
          style: FilledButton.styleFrom(
            backgroundColor: Colors.black,
            foregroundColor: Colors.white,
            minimumSize: const Size.fromHeight(52),
            shape: const StadiumBorder(),
            textStyle: const TextStyle(fontFamily: 'packages/digid/Inter', fontSize: 16, fontWeight: FontWeight.w500),
          ),
        ),
      ),
      home: const HomePage(),
    );
  }
}
