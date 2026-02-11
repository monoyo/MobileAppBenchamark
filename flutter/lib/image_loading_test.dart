import 'dart:async';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_cache_manager/flutter_cache_manager.dart';
import 'models/test_result.dart';
import 'config/sample_configuration.dart';

/// Image loading benchmark page.
/// Tests network performance with retry logic, caching, and stream management.
/// Similar to Java image loading test with timeout, retry, and batching.
class ImageLoadingTest extends StatefulWidget {
  final int runId;
  
  const ImageLoadingTest({
    super.key,
    required this.runId,
  });

  @override
  State<ImageLoadingTest> createState() => _ImageLoadingTestState();
}

class _ImageLoadingTestState extends State<ImageLoadingTest> {
  static const List<String> _imageUrls = [
    "https://fastly.picsum.photos/id/861/300/200.jpg?hmac=SePZxFhkEpm4mmZIJke4z7ghH-2l0PsNAtEm_2vq2W4",
    "https://fastly.picsum.photos/id/687/300/200.jpg?hmac=4cY--ZSfxEMRzYtVmyvUBPrHqzAqJ3JmMSEmdYqdfMM",
    "https://fastly.picsum.photos/id/408/300/200.jpg?hmac=WLBoOapFRUAh4eGfCSPD4htVThRV8LKEnheDBbmOYvY",
    "https://fastly.picsum.photos/id/297/300/200.jpg?hmac=FHS6m7Ec_3-9rDv45kvf5XCQz0tWD5sY9yZY7GwSC6c",
    "https://fastly.picsum.photos/id/723/300/200.jpg?hmac=r-Bu4Me1tZJW3ncPjx4Pj2nhJ2sV0XQhDEeM1kH9EyY",
    "https://fastly.picsum.photos/id/507/300/200.jpg?hmac=H7vqiU7dtXTNLQraEHG25D7naP8nQy-uGlbyUCvE6Mo",
    "https://fastly.picsum.photos/id/163/300/200.jpg?hmac=fHGMH6DT42ra3SOzs6JtojmYZ7jECNcq5xn1Ap9OPNA",
    "https://fastly.picsum.photos/id/54/300/200.jpg?hmac=7Cm5bybfBDMHwUF7AvEbAKWA7l5WnE9MZvcZhPpULTc",
    "https://fastly.picsum.photos/id/992/300/200.jpg?hmac=w137wSlXMe7QugWkdz2qvxFlif1dwEWqNnv4qFIyWps",
    "https://fastly.picsum.photos/id/764/300/200.jpg?hmac=1sBuxBDUdVzEEnIKB5S4cXJ_sQ5Tp3ZSnjrHOWF_E20",
  ];

  // Performance tracking
  late final int _startMs;
  int _lastProgressMs = 0;
  
  // Configuration constants
  static const int _maxDurationMs = 60 * 1000 * 10; // increase timeout for 10k samples
  static const int _stallTimeoutMs = 10 * 1000; // 10 second watchdog
  static const int _maxRetries = 2;
  static const int _scrollItemHeight = 200;

  // State tracking
  late final ScrollController _scrollController;
  late final List<ImageProvider?> _imageProviders;
  late final List<bool> _loadSuccessful;
  late final List<bool> _loadAttempted;
  late final List<int> _retryCount;
  late final List<bool> _isResolving;

  int _currentLoadIndex = 0;
  int _successfulLoads = 0;
  int _failedLoads = 0;
  bool _testCompleted = false;
  bool _reachedEnd = false;

  Timer? _scrollTimer;
  Timer? _watchdogTimer;
  BaseCacheManager? _cacheManager;

  int get _itemCount => SampleConfig.sampleCount;

  @override
  void initState() {
    super.initState();
    _scrollController = ScrollController();
    _startMs = DateTime.now().millisecondsSinceEpoch;
    _lastProgressMs = _startMs;

    // Initialize state tracking lists
    _imageProviders = List<ImageProvider?>.filled(_itemCount, null);
    _loadSuccessful = List<bool>.filled(_itemCount, false);
    _loadAttempted = List<bool>.filled(_itemCount, false);
    _retryCount = List<int>.filled(_itemCount, 0);
    _isResolving = List<bool>.filled(_itemCount, false);

    _cacheManager = DefaultCacheManager();

    _setupScrollListener();
    _setupWatchdogTimer();
    _startScrollingSequence();
  }

  /// Sets up scroll listener to detect when all images are visible.
  void _setupScrollListener() {
    _scrollController.addListener(() {
      if (_testCompleted || !mounted) return;
      
      if (_scrollController.hasClients) {
        final maxScroll = _scrollController.position.maxScrollExtent;
        final currentScroll = _scrollController.position.pixels;
        
        if (currentScroll >= maxScroll - 1.0 && !_reachedEnd) {
          _reachedEnd = true;
          _checkCompletion();
        }
      }
    });
  }

  /// Sets up watchdog timer to detect stalls and force completion.
  void _setupWatchdogTimer() {
    _watchdogTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
      if (!mounted || _testCompleted) {
        timer.cancel();
        return;
      }

      final now = DateTime.now().millisecondsSinceEpoch;
      if (now - _startMs > _maxDurationMs ||
          now - _lastProgressMs > _stallTimeoutMs) {
        _completeWithTimeout();
        timer.cancel();
      }
    });
  }

  /// Starts automatic scrolling sequence.
  void _startScrollingSequence() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scrollController.hasClients) return;
      
      _scrollTimer = Timer.periodic(const Duration(milliseconds: 500), (timer) {
        if (!mounted || _testCompleted || !_scrollController.hasClients) {
          timer.cancel();
          return;
        }

        final targetScroll = _currentLoadIndex * _scrollItemHeight.toDouble();
        if (_scrollController.offset < targetScroll) {
          _scrollController.animateTo(
            targetScroll,
            duration: const Duration(milliseconds: 300),
            curve: Curves.easeOut,
          );
        }
      });
    });
  }

  @override
  void dispose() {
    _scrollTimer?.cancel();
    _watchdogTimer?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  /// Marks an image load attempt as complete.
  void _markLoadComplete(int index, {required bool success}) {
    if (_testCompleted || !mounted || index < 0 || index >= _itemCount) return;
    if (_loadAttempted[index]) return;

    _loadAttempted[index] = true;
    _lastProgressMs = DateTime.now().millisecondsSinceEpoch;

    if (success) {
      _successfulLoads++;
      _loadSuccessful[index] = true;
    } else {
      _failedLoads++;
      _loadSuccessful[index] = false;
    }

    // Move to next image after a short delay
    Future.delayed(const Duration(milliseconds: 100), () {
      if (!mounted || _testCompleted) return;
      if (_currentLoadIndex < _itemCount - 1) {
        _currentLoadIndex++;
        setState(() {});
      }
      _checkCompletion();
    });
  }

  /// Checks if test should complete.
  void _checkCompletion() {
    if (_testCompleted || !mounted) return;

    final allAttempted = _successfulLoads + _failedLoads >= _itemCount;
    if (allAttempted && _reachedEnd) {
      _completeTest();
    }
  }

  /// Completes test successfully.
  void _completeTest() {
    if (_testCompleted || !mounted) return;
    
    _testCompleted = true;
    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startMs;
    final result = TestResult(
      'Image Loading Test',
      elapsedMs,
      'loaded=$_successfulLoads, failed=$_failedLoads',
      true,
    );

    Navigator.pop(context, result);
  }

  /// Completes test due to timeout.
  void _completeWithTimeout() {
    if (_testCompleted || !mounted) return;

    // Mark remaining as failed
    for (int i = 0; i < _itemCount; i++) {
      if (!_loadAttempted[i]) {
        _loadAttempted[i] = true;
        _failedLoads++;
      }
    }

    _testCompleted = true;
    final elapsedMs = DateTime.now().millisecondsSinceEpoch - _startMs;
    final result = TestResult(
      'Image Loading Test',
      elapsedMs,
      'loaded=$_successfulLoads, failed=$_failedLoads (timeout)',
      true,
    );

    Navigator.pop(context, result);
  }

  /// Resolves image provider and tracks load completion.
  void _resolveImageAndTrack(int index, ImageProvider provider) {
    if (!mounted || _testCompleted || _loadAttempted[index]) return;
    if (_isResolving[index]) return;

    _isResolving[index] = true;
    final stream = provider.resolve(const ImageConfiguration());
    ImageStreamListener? listener;

    listener = ImageStreamListener(
      (ImageInfo info, bool syncCall) {
        try {
          _isResolving[index] = false;
          _markLoadComplete(index, success: true);
        } finally {
          stream.removeListener(listener!);
        }
      },
      onError: (dynamic error, StackTrace? stackTrace) {
        stream.removeListener(listener!);
        _isResolving[index] = false;

        if (_retryCount[index] < _maxRetries && !_loadAttempted[index]) {
          // Exponential backoff retry
          _retryCount[index]++;
          final delayMs = 250 * (1 << (_retryCount[index] - 1));
          
          Future.delayed(Duration(milliseconds: delayMs), () {
            if (!mounted || _testCompleted || _loadAttempted[index]) return;
            setState(() {});
          });
        } else {
          _markLoadComplete(index, success: false);
        }
      },
    );

    stream.addListener(listener);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: ListView.builder(
        controller: _scrollController,
        itemExtent: _scrollItemHeight.toDouble(),
        cacheExtent: 2000,
        itemCount: _itemCount,
        itemBuilder: (context, index) => _buildImageTile(index),
      ),
    );
  }

  /// Builds a single image tile based on load state.
  Widget _buildImageTile(int index) {
    // Current loading image
    if (index == _currentLoadIndex) {
      return _buildLoadingImage(index);
    }

    // Previously loaded images
    if (index < _currentLoadIndex) {
      return _buildLoadedImage(index);
    }

    // Upcoming images
    return _buildWaitingImage();
  }

  /// Builds the currently loading image tile.
  Widget _buildLoadingImage(int index) {
    return SizedBox(
      height: _scrollItemHeight.toDouble(),
      child: CachedNetworkImage(
        imageUrl: _imageUrls[index % _imageUrls.length],
        cacheManager: _cacheManager,
        httpHeaders: const {
          'Cache-Control': 'no-cache, no-store, must-revalidate',
          'Pragma': 'no-cache',
          'Expires': '0',
        },
        placeholder: (context, url) => const Center(
          child: CircularProgressIndicator(),
        ),
        errorWidget: (context, url, error) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _markLoadComplete(index, success: false);
          });
          return _buildErrorWidget();
        },
        imageBuilder: (context, imageProvider) {
          _imageProviders[index] = imageProvider;
          _resolveImageAndTrack(index, imageProvider);
          return Image(image: imageProvider, fit: BoxFit.cover);
        },
      ),
    );
  }

  /// Builds a previously loaded image tile.
  Widget _buildLoadedImage(int index) {
    final provider = _imageProviders[index];
    final successful = _loadSuccessful[index];

    if (successful && provider != null) {
      return Image(image: provider, fit: BoxFit.cover);
    }

    return _buildErrorWidget();
  }

  /// Builds a waiting image tile (not yet loading).
  Widget _buildWaitingImage() {
    return Container(
      height: _scrollItemHeight.toDouble(),
      alignment: Alignment.center,
      color: Colors.grey[100],
      child: Text(
        'Waiting... ($_currentLoadIndex/$_itemCount)',
        style: TextStyle(color: Colors.grey[600]),
      ),
    );
  }

  /// Builds error display widget.
  Widget _buildErrorWidget() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: const [
          Icon(Icons.error, color: Colors.red),
          SizedBox(width: 6),
          Expanded(
            child: Text(
              'Failed to load image',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(color: Colors.red),
            ),
          ),
        ],
      ),
    );
  }
}
