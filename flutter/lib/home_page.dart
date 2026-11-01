import 'dart:async';

import 'package:digid/digid.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'backend.dart';
import 'main.dart';
import 'result_page.dart';
import 'strings.dart';

/// The app's own start screen: what to verify, the back of the card, the language, and an
/// evaluation banner when the SDK runs without a valid licence.
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  bool _selfie = false;
  bool _scanBack = false;
  bool _opening = false;

  Future<void> _start(Digid nida) async {
    if (_opening) return; // a double tap opens one flow
    _opening = true;
    try {
      // The flow's config. Every key is optional; the default layout is the minimal one, made
      // to sit inside the app. The same config can come from JSON: DigidFlowConfig.fromJson.
      final config = DigidFlowConfig(
        flow: FlowSteps(
          steps: _selfie ? const [FlowStep.card, FlowStep.selfie] : const [FlowStep.card],
          // `required`: the app owns this switch. `optional` would show the flow's own one.
          requireBack: _scanBack ? RequireBack.required : RequireBack.off,
        ),
      );

      // Pushes the flow's screens and returns its result, or null if the user left the flow.
      final result = await DigidFlow.start(
        context,
        engine: nida,
        config: config,
        language: appLang, // the app's language; the open flow follows a change
        // For logs and analytics only: the flow shows its own error screens.
        onStepChanged: (stage) => _log('step ${stage.wire}'),
        onError: (error) => _log('error ${error.runtimeType}'),
      );
      if (result == null) return;
      final payload = result.payload; // null only if it could not be built (reported to onError)
      if (payload != null) unawaited(sendToBackend(payload));
      if (!mounted) return;
      await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ResultPage(result: result)));
    } finally {
      _opening = false;
      if (mounted) setState(() {}); // the licence state can change during a flow
    }
  }

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Lang>(
      valueListenable: appLang,
      builder: (context, lang, _) => Scaffold(
        body: SafeArea(
          child: FutureBuilder<Digid>(
            future: engine,
            builder: (context, snap) {
              if (snap.hasError) {
                _log('engine failed to start: ${snap.error.runtimeType}');
                return _Message(
                  tr('The app could not start', 'Programu haikuweza kuanza'),
                  tr('Close the app and open it again.', 'Funga programu na uifungue tena.'),
                );
              }
              return _home(context, lang, snap.data);
            },
          ),
        ),
      ),
    );
  }

  Widget _home(BuildContext context, Lang lang, Digid? nida) {
    final t = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
      children: [
        Row(
          children: [
            Text('Mfano', style: t.titleLarge?.copyWith(fontWeight: FontWeight.w500)),
            const Spacer(),
            // The app's own language control. The flow has none.
            SegmentedButton<Lang>(
              segments: const [
                ButtonSegment(value: Lang.en, label: Text('EN')),
                ButtonSegment(value: Lang.sw, label: Text('SW')),
              ],
              selected: {lang},
              showSelectedIcon: false,
              onSelectionChanged: (s) => appLang.value = s.first,
            ),
          ],
        ),
        if (nida != null && nida.licence().isEvaluation) ...[
          const SizedBox(height: 16),
          _EvaluationBanner(
            heading: tr('Evaluation mode', 'Hali ya majaribio'),
            message: digidLicence.isEmpty
                ? tr(
                    'No licence key. Results are watermarked and never better than Review.',
                    'Hakuna ufunguo wa leseni. Matokeo yana alama ya majaribio na hayazidi "Inahitaji ukaguzi".',
                  )
                : tr(
                    'This licence key is not valid for this app id. Results are watermarked and never better than Review.',
                    'Ufunguo huu wa leseni si halali kwa programu hii. Matokeo yana alama ya majaribio na hayazidi "Inahitaji ukaguzi".',
                  ),
          ),
        ],
        const SizedBox(height: 40),
        Text(tr('Verify your identity', 'Thibitisha utambulisho wako'), style: t.headlineMedium?.copyWith(fontWeight: FontWeight.w300)),
        const SizedBox(height: 8),
        Text(
          tr(
            'Your NIDA ID card or driving licence, read right here on this phone.',
            'Kitambulisho chako cha NIDA au leseni ya udereva, kinasomwa papo hapo kwenye simu hii.',
          ),
          style: t.bodyLarge?.copyWith(color: Colors.black54),
        ),
        const SizedBox(height: 32),
        RadioGroup<bool>(
          groupValue: _selfie,
          onChanged: (v) => setState(() => _selfie = v ?? false),
          child: Column(
            children: [
              _ChoiceTile(
                value: false,
                title: tr('ID card', 'Kitambulisho'),
                subtitle: tr('The front of the card.', 'Upande wa mbele wa kitambulisho.'),
              ),
              const SizedBox(height: 8),
              _ChoiceTile(
                value: true,
                title: tr('ID card and selfie', 'Kitambulisho na selfie'),
                subtitle: tr('The card, then a quick selfie.', 'Kitambulisho, kisha selfie fupi.'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 8),
        SwitchListTile(
          value: _scanBack,
          onChanged: (v) => setState(() => _scanBack = v),
          title: Text(tr('Scan the back too', 'Soma na upande wa nyuma')),
          subtitle: Text(tr('Turn the card over after the front.', 'Geuza kadi baada ya upande wa mbele.')),
          contentPadding: const EdgeInsets.symmetric(horizontal: 16),
        ),
        const SizedBox(height: 32),
        FilledButton(
          key: const ValueKey('start'),
          onPressed: nida == null ? null : () => _start(nida),
          child: nida == null ? Text(tr('Getting ready…', 'Inaandaa…')) : Text(tr('Start verification', 'Anza uthibitisho')),
        ),
      ],
    );
  }
}

class _ChoiceTile extends StatelessWidget {
  const _ChoiceTile({required this.value, required this.title, required this.subtitle});
  final bool value;
  final String title, subtitle;

  @override
  Widget build(BuildContext context) => Card.outlined(
    margin: EdgeInsets.zero,
    clipBehavior: Clip.antiAlias,
    child: RadioListTile<bool>(value: value, title: Text(title), subtitle: Text(subtitle)),
  );
}

/// Shown while the engine runs in evaluation mode: no key, or a key issued for another app id.
class _EvaluationBanner extends StatelessWidget {
  const _EvaluationBanner({required this.heading, required this.message});
  final String heading, message;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(color: const Color(0xFFFFF4DB), borderRadius: BorderRadius.circular(16)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.info_outline, color: Color(0xFF8A5A00)),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(heading, style: t.titleSmall),
                const SizedBox(height: 4),
                Text(message, style: t.bodySmall),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _Message extends StatelessWidget {
  const _Message(this.heading, this.message);
  final String heading, message;

  @override
  Widget build(BuildContext context) {
    final t = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(heading, style: t.titleLarge, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(message, style: t.bodyMedium, textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}

/// Debug builds only, and never personal data: the result holds names, numbers and photos.
void _log(String message) {
  if (kDebugMode) debugPrint('digid example: $message');
}
