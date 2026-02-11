import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../models/post.dart';

/// API services for fetching and managing network requests.
/// Implements HTTP client pooling and proper resource cleanup.
class ApiService {
  static const String _apiBaseUrl = 'https://jsonplaceholder.typicode.com';
  static const String _postsEndpoint = '/posts';
  
  late final Uri _postsUri;
  late final http.Client _httpClient;

  ApiService() {
    _postsUri = Uri.parse('$_apiBaseUrl$_postsEndpoint');
    _httpClient = http.Client();
  }

  /// Fetches posts from the API with timeout and error handling.
  /// 
  /// Returns a list of [Post] objects on success.
  /// Throws [Exception] with descriptive message on failure.
  Future<List<Post>> fetchPosts({Duration? timeout}) async {
    try {
      final response = await _httpClient
          .get(
            _postsUri,
            headers: {
              'Accept': 'application/json',
              'Content-Type': 'application/json',
            },
          )
          .timeout(
            timeout ?? const Duration(seconds: 30),
            onTimeout: () => throw TimeoutException(
              'API request timed out after ${timeout?.inSeconds ?? 30}s',
            ),
          );

      if (response.statusCode != 200) {
        throw HttpException(
          'API returned status ${response.statusCode}',
          response: response,
        );
      }

      return _parsePostsResponse(response.body);
    } on SocketException catch (e) {
      throw NetworkException('Network error: ${e.message}');
    } on TimeoutException catch (e) {
      throw TimeoutException(e.message ?? 'Request timeout');
    } catch (e) {
      throw Exception('Failed to fetch posts: $e');
    }
  }

  /// Parses JSON response body into list of Post objects.
  static List<Post> _parsePostsResponse(String body) {
    try {
      final List<dynamic> jsonData = jsonDecode(body);
      
      return jsonData
          .whereType<Map<String, dynamic>>()
          .map(Post.fromJson)
          .toList();
    } catch (e) {
      throw FormatException('Failed to parse API response: $e');
    }
  }

  /// Closes HTTP client and frees resources.
  void close() {
    _httpClient.close();
  }
}

/// Exception thrown on network errors.
class NetworkException implements Exception {
  final String message;
  NetworkException(this.message);
  
  @override
  String toString() => message;
}

/// Exception thrown on HTTP protocol errors.
class HttpException implements Exception {
  final String message;
  final http.Response? response;
  
  HttpException(this.message, {this.response});
  
  @override
  String toString() => message;
}

/// Exception thrown on request timeout.
class TimeoutException implements Exception {
  final String? message;
  TimeoutException(this.message);
  
  @override
  String toString() => message ?? 'Request timeout';
}
