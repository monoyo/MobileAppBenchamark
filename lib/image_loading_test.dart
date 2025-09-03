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
  bool _reachedEnd = false;
  // Watchdog timers to ensure completion
  Timer? _watchdogTimer;
  late int _lastProgressMs;
  static const int _maxRunMs = 20000; // hard cap per run (20s)
  static const int _stallMs = 4000; // consider stalled if no progress for 4s
  CacheManager? _cacheManager;

  @override
  void initState() {
    super.initState();
    start = DateTime.now().millisecondsSinceEpoch;
  _lastProgressMs = start;
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
        final double next = _autoOffset + 220.0;
        if (next >= max) {
          _autoOffset = max;
          _scrollController.animateTo(
            _autoOffset,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
          );
          _reachedEnd = true;
          t.cancel();
          _tryFinish();
        } else {
          _autoOffset = next;
          _scrollController.animateTo(
            _autoOffset,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOut,
          );
        }
      }
    });

    // Also mark reached end via listener in case of manual scroll
    _scrollController.addListener(() {
      if (!mounted || _completed) return;
      if (_scrollController.hasClients) {
        final position = _scrollController.position;
        if (position.pixels >= position.maxScrollExtent - 1.0) {
          if (!_reachedEnd) {
            _reachedEnd = true;
            _tryFinish();
          }
        }
      }
    });

    // Watchdog to avoid getting stuck or running too long
    _watchdogTimer = Timer.periodic(const Duration(milliseconds: 500), (Timer t) {
      if (!mounted || _completed) {
        t.cancel();
        return;
      }
      final int now = DateTime.now().millisecondsSinceEpoch;
      if (now - start > _maxRunMs || now - _lastProgressMs > _stallMs) {
        _completeWithFailures();
        t.cancel();
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
  _lastProgressMs = DateTime.now().millisecondsSinceEpoch;
    setState(() {
      // trigger rebuild dla danego elementu
    });
    _retryQueued.remove(index);
  }

  @override
  void dispose() {
    _autoScrollTimer?.cancel();
    _scrollController.dispose();
    _watchdogTimer?.cancel();
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
  _lastProgressMs = DateTime.now().millisecondsSinceEpoch;

    if ((loaded + failed) >= _itemCount) {
      _tryFinish();
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

  void _tryFinish() {
    if (_completed || !mounted) return;
    if ((loaded + failed) >= _itemCount && _reachedEnd) {
      _completed = true;
      final int elapsed = DateTime.now().millisecondsSinceEpoch - start;
      final TestResult res = TestResult(
        'Image Loading Test',
        elapsed,
        'loaded=$loaded, failed=$failed',
        true,
      );
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) Navigator.pop(context, res);
      });
    }
  }

  void _completeWithFailures() {
    if (_completed || !mounted) return;
    for (int i = 0; i < _itemCount; i++) {
      if (!_done[i]) {
        _done[i] = true;
        failed++;
      }
    }
    _completed = true;
    final int elapsed = DateTime.now().millisecondsSinceEpoch - start;
    final TestResult res = TestResult(
      'Image Loading Test',
      elapsed,
      'loaded=$loaded, failed=$failed (watchdog)',
      true,
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context, res);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        controller: _scrollController,
  itemExtent: 200,
  cacheExtent: 2000,
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
