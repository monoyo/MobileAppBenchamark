import 'package:flutter/material.dart';
import 'dart:async';
import 'models/test_result.dart';
import 'package:cached_network_image/cached_network_image.dart';

class ImageLoadingTestPage extends StatefulWidget {
  final int runId; // unique per iteration to avoid cache hits
  const ImageLoadingTestPage({super.key, required this.runId});

  @override
  State<ImageLoadingTestPage> createState() => _ImageLoadingTestPageState();
}

class _ImageLoadingTestPageState extends State<ImageLoadingTestPage> {
  final ScrollController _scrollController = ScrollController();

  List<String> urls = [
    "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
    "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
    "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
    "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
    "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
    "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
    "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
    "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
    "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
    "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20"
  ];

  int loaded = 0;
  int failed = 0;
  late List<bool> _done;
  bool _completed = false;
  late int start;
  late List<String> _runUrls; // urls augmented with runId to prevent cache reuse
  Timer? _autoScrollTimer;
  double _autoOffset = 0.0;

  @override
  void initState() {
    super.initState();
    start = DateTime.now().millisecondsSinceEpoch;
    _done = List<bool>.filled(urls.length, false);
    // Bust caches between iterations by appending a unique query param per run
    final int run = widget.runId;
    _runUrls = List<String>.generate(
      urls.length,
      (int i) => '${urls[i]}&r=$run&idx=$i',
      growable: false,
    );
    // Nudge an initial small scroll to ensure list attaches and scroll physics engage
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          50.0,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });

    // Start periodic auto-scroll so all items get built even if images are cached
    _autoScrollTimer = Timer.periodic(const Duration(milliseconds: 300), (Timer t) {
      if (!mounted || _completed) {
        t.cancel();
        return;
      }
      if (_scrollController.hasClients) {
        final double max = _scrollController.position.maxScrollExtent;
        _autoOffset = (_autoOffset + 220.0).clamp(0.0, max);
        _scrollController.animateTo(
          _autoOffset,
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeOut,
        );
        if (_autoOffset >= max) {
          // if we reached the end, bounce back a bit to trigger potential rebuilds
          _autoOffset = (max - 10.0).clamp(0.0, max);
        }
      }
    });
  }

  @override
  void dispose() {
    _autoScrollTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _markDone(int index, {required bool success}) {
    if (_completed || !mounted) return;
    if (index < 0 || index >= urls.length) return;
    if (_done[index]) return; // licz tylko raz na obrazek
    _done[index] = true;
    if (success) {
      loaded++;
    } else {
      failed++;
    }

    if ((loaded + failed) >= urls.length) {
      if (_completed) return;
      _completed = true;
      final elapsed = DateTime.now().millisecondsSinceEpoch - start;
      final res = TestResult(
        'Image Loading Test',
        elapsed,
        'loaded=$loaded, failed=$failed',
        true,
      );
      Navigator.pop(context, res);
    } else {
      // przewiń do ostatnio przetworzonego indeksu
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            index * 200.0,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        controller: _scrollController,
    itemCount: urls.length,
        itemBuilder: (c, i) {
          return SizedBox(
            height: 200,
            child: CachedNetworkImage(
      imageUrl: _runUrls[i],
              placeholder: (c, u) =>
                  const Center(child: CircularProgressIndicator()),
              errorWidget: (c, u, e) {
                // Zlicz błąd tylko raz dla danego indeksu
                WidgetsBinding.instance.addPostFrameCallback((_) => _markDone(i, success: false));
                return Wrap(
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    const Icon(Icons.error, color: Colors.red),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        'Error loading image',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Colors.red),
                      ),
                    ),
                  ],
                );
              },
              imageBuilder: (ctx, imageProvider) {
                // Zlicz sukces tylko raz dla danego indeksu
                WidgetsBinding.instance
                    .addPostFrameCallback((_) => _markDone(i, success: true));
                return Image(image: imageProvider, fit: BoxFit.cover);
              },
            ),
          );
        },
      ),
    );
  }
}
