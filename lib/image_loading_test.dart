import 'package:flutter/material.dart';
import 'dart:async';
import 'models/test_result.dart';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

class ImageLoadingTestPage extends StatefulWidget {
  final int runId; // unique per iteration to avoid cache hits
  const ImageLoadingTestPage({super.key, required this.runId});

  @override
  State<ImageLoadingTestPage> createState() => _ImageLoadingTestPageState();
}

class _ImageLoadingTestPageState extends State<ImageLoadingTestPage> {
  final ScrollController _scrollController = ScrollController();
  // Liczba obrazów do przetworzenia w jednej iteracji
  final int _itemCount = 20;

  int loaded = 0;
  int failed = 0;
  late List<bool> _done;
  bool _completed = false;
  late int start;
  late List<String> _runUrls; // urls augmented with runId to prevent cache reuse
  Timer? _autoScrollTimer;
  double _autoOffset = 0.0;
  late List<int> _retries; // retry count per index
  final Set<int> _retryQueued = <int>{};
  static const int _maxRetries = 2;
  CacheManager? _cacheManager;

  @override
  void initState() {
    super.initState();
    start = DateTime.now().millisecondsSinceEpoch;
    _done = List<bool>.filled(_itemCount, false);
  // Przygotuj listy i URL-e na start
  _retries = List<int>.filled(_itemCount, 0);
  _runUrls = List<String>.generate(_itemCount, (int i) => _urlForIndex(i, 0), growable: true);
    // Per-iteration CacheManager with zero staleness to avoid cache reuse
    _cacheManager = CacheManager(
      Config(
        'run_${widget.runId}',
        stalePeriod: Duration.zero,
        maxNrOfCacheObjects: 50,
      ),
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

  String _urlForIndex(int index, int retry) {
    final int run = widget.runId;
    // seed zapewnia deterministyczny obraz, retry param wymusza ponowny fetch gdy potrzebny
    return 'https://picsum.photos/seed/${run}_$index/300/200?rt=$retry';
  }

  void _retryIndex(int index) {
    if (!mounted || _completed) return;
    if (index < 0 || index >= _itemCount) return;
    if (_done[index]) return;
    if (_retries[index] >= _maxRetries) {
      _markDone(index, success: false);
      return;
    }
    if (_retryQueued.contains(index)) return; // już w kolejce
    _retryQueued.add(index);
    _retries[index] = _retries[index] + 1;
    _runUrls[index] = _urlForIndex(index, _retries[index]);
    setState(() {
      // trigger rebuild dla danego elementu
    });
    _retryQueued.remove(index);
  }

  @override
  void dispose() {
    _autoScrollTimer?.cancel();
    _scrollController.dispose();
  _cacheManager?.dispose();
    super.dispose();
  }

  void _markDone(int index, {required bool success}) {
    if (_completed || !mounted) return;
  if (index < 0 || index >= _itemCount) return;
    if (_done[index]) return; // licz tylko raz na obrazek
    _done[index] = true;
    if (success) {
      loaded++;
    } else {
      failed++;
    }

  if ((loaded + failed) >= _itemCount) {
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
  itemCount: _itemCount,
        itemBuilder: (c, i) {
          return SizedBox(
            height: 200,
            child: CachedNetworkImage(
      imageUrl: _runUrls[i],
              cacheManager: _cacheManager,
              httpHeaders: const <String, String>{
                'Cache-Control': 'no-cache, no-store, must-revalidate',
                'Pragma': 'no-cache',
                'Expires': '0',
              },
              placeholder: (c, u) =>
                  const Center(child: CircularProgressIndicator()),
              errorWidget: (c, u, e) {
                // Spróbuj ponowić pobranie do _maxRetries razy zanim policzymy błąd
                WidgetsBinding.instance.addPostFrameCallback((_) => _retryIndex(i));
                return Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8.0),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
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
                  ),
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
