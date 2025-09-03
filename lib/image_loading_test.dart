import 'dart:async';

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';

import 'models/test_result.dart';

class ImageLoadingTestPage extends StatefulWidget {
  final int runId; // unique per iteration to avoid cache hits
  const ImageLoadingTestPage({super.key, required this.runId});

  @override
  State<ImageLoadingTestPage> createState() => _ImageLoadingTestPageState();
}

class _ImageLoadingTestPageState extends State<ImageLoadingTestPage> {
  final ScrollController _scrollController = ScrollController();
  final int _itemCount = 20;

  late final List<String> _runUrls;
  late final List<ImageProvider?> _providers;
  late final List<bool> _successList;
  late final List<bool> _done;
  late final List<int> _retries;
  late final List<bool> _resolving; // true when waiting for ImageStream
  final Set<int> _retryQueued = <int>{};

  int _currentIndex = 0;
  int _loaded = 0;
  int _failed = 0;
  bool _completed = false;
  bool _reachedEnd = false;

  Timer? _autoScrollTimer;
  Timer? _watchdogTimer;
  BaseCacheManager? _cacheManager;

  // timing and limits
  late final int _startMs;
  int _lastProgressMs = 0;
  final int _maxRunMs = 60 * 1000; // 60s
  final int _stallMs = 10 * 1000; // 10s
  final int _maxRetries = 2;

  @override
  void initState() {
    super.initState();
    _cacheManager = DefaultCacheManager();
    _runUrls = List<String>.generate(_itemCount, (i) => _urlForIndex(i, 0));
    _providers = List<ImageProvider?>.filled(_itemCount, null);
    _successList = List<bool>.filled(_itemCount, false);
    _done = List<bool>.filled(_itemCount, false);
  _retries = List<int>.filled(_itemCount, 0);
  _resolving = List<bool>.filled(_itemCount, false);

    _startMs = DateTime.now().millisecondsSinceEpoch;
    _lastProgressMs = _startMs;

    // small nudge so ListView attaches
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

    // watchdog
    _watchdogTimer = Timer.periodic(const Duration(milliseconds: 500), (t) {
      if (!mounted || _completed) {
        t.cancel();
        return;
      }
      final int now = DateTime.now().millisecondsSinceEpoch;
      if (now - _startMs > _maxRunMs || now - _lastProgressMs > _stallMs) {
        _completeWithFailures();
        t.cancel();
      }
    });
  }

  String _urlForIndex(int index, int retry) {
    final int run = widget.runId;
    return 'https://picsum.photos/seed/${run}_$index/300/200?rt=$retry';
  }

  // Retry logic kept inline where needed; helper removed to avoid unused warning

  @override
  void dispose() {
    _autoScrollTimer?.cancel();
    _watchdogTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _markDone(int index, {required bool success}) {
    if (_completed || !mounted) return;
    if (index < 0 || index >= _itemCount) return;
    if (_done[index]) return;
    _done[index] = true;
    if (success) {
      _loaded++;
      _successList[index] = true;
    } else {
      _failed++;
      _successList[index] = false;
    }
    _lastProgressMs = DateTime.now().millisecondsSinceEpoch;

    if ((_loaded + _failed) >= _itemCount) {
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        if (_scrollController.hasClients) {
          final double max = _scrollController.position.maxScrollExtent;
          await _scrollController.animateTo(
            max,
            duration: const Duration(milliseconds: 350),
            curve: Curves.easeOut,
          );
          _reachedEnd = true;
        }
        _tryFinish();
      });
    } else {
      _currentIndex++;
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        setState(() {});
        if (_scrollController.hasClients) {
          await _scrollController.animateTo(
            _currentIndex * 200.0,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  void _tryFinish() {
    if (_completed || !mounted) return;
    if ((_loaded + _failed) >= _itemCount && _reachedEnd) {
      _completed = true;
      final int elapsed = DateTime.now().millisecondsSinceEpoch - _startMs;
      final TestResult res = TestResult('Image Loading Test', elapsed, 'loaded=$_loaded, failed=$_failed', true);
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
        _failed++;
      }
    }
    _completed = true;
    final int elapsed = DateTime.now().millisecondsSinceEpoch - _startMs;
    final TestResult res = TestResult('Image Loading Test', elapsed, 'loaded=$_loaded, failed=$_failed (watchdog)', true);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) Navigator.pop(context, res);
    });
  }

  // Resolve an ImageProvider to ensure the image is decoded. If decoding
  // succeeds, mark the item done; otherwise retry with backoff up to limit.
  void _resolveProviderAndMark(int index, ImageProvider provider) {
    if (!mounted || _completed) return;
    if (index < 0 || index >= _itemCount) return;
    if (_done[index]) return;
    if (_resolving[index]) return; // already resolving

    _resolving[index] = true;

    final ImageStream stream = provider.resolve(const ImageConfiguration());
    ImageStreamListener? listener;
    listener = ImageStreamListener((ImageInfo info, bool syncCall) {
      // success: image decoded
      try {
        _resolving[index] = false;
        _markDone(index, success: true);
      } finally {
        stream.removeListener(listener!);
      }
    }, onError: (dynamic error, StackTrace? stackTrace) {
      stream.removeListener(listener!);
      _resolving[index] = false;
      // schedule retry with simple backoff
      if (_retries[index] < _maxRetries && !_done[index]) {
        _retries[index]++;
        final int delayMs = 250 * (1 << (_retries[index] - 1));
        Future.delayed(Duration(milliseconds: delayMs), () {
          if (!mounted || _completed || _done[index]) return;
          // re-generate the URL to bypass caches when retrying
          _runUrls[index] = _urlForIndex(index, _retries[index]);
          setState(() {});
        });
      } else {
        _markDone(index, success: false);
      }
    });

    stream.addListener(listener);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Image Loading Test')),
      body: ListView.builder(
        controller: _scrollController,
        itemExtent: 200,
        cacheExtent: 2000,
        itemCount: _itemCount,
        itemBuilder: (c, i) {
          if (i == _currentIndex) {
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
                placeholder: (c, u) => const Center(child: CircularProgressIndicator()),
                errorWidget: (c, u, e) {
                  WidgetsBinding.instance.addPostFrameCallback((_) => _markDone(i, success: false));
                  return Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8.0),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(Icons.error, color: Colors.red),
                        SizedBox(width: 6),
                        Text('Error loading image', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: Colors.red)),
                      ],
                    ),
                  );
                },
                imageBuilder: (ctx, imageProvider) {
                  // store provider for later reuse
                  _providers[i] = imageProvider;
                  // resolve provider and ensure the image was decoded successfully
                  _resolveProviderAndMark(i, imageProvider);
                  return Image(image: imageProvider, fit: BoxFit.cover);
                },
              ),
            );
          }

          if (i < _currentIndex) {
            final prov = _providers[i];
            final status = _successList[i];
            if (status == true && prov != null) {
              return SizedBox(height: 200, child: Image(image: prov, fit: BoxFit.cover));
            }
            return Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8.0),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: const [
                  Icon(Icons.error, color: Colors.red),
                  SizedBox(width: 6),
                  Text('Error loading image', maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: Colors.red)),
                ],
              ),
            );
          }

          return Container(
            height: 200,
            alignment: Alignment.center,
            child: const Text('Waiting...'),
          );
        },
      ),
    );
  }
}
