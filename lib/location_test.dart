import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'models/test_result.dart';

class LocationTestPage extends StatefulWidget {
  const LocationTestPage({super.key});

  @override
  State<LocationTestPage> createState() => _LocationTestPageState();
}

class _LocationTestPageState extends State<LocationTestPage> {
  late int start;

  @override
  void initState() {
    super.initState();
    _start();
  }

  void _start() async {
    start = DateTime.now().millisecondsSinceEpoch;
    bool service = await Geolocator.isLocationServiceEnabled();
    if (!service) {
      Navigator.pop(context, TestResult('Location Test', -1, 'Location services disabled', false));
      return;
    }
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        Navigator.pop(context, TestResult('Location Test', -1, 'Permission denied', false));
        return;
      }
    }
    if (permission == LocationPermission.deniedForever) {
      Navigator.pop(context, TestResult('Location Test', -1, 'Permission denied forever', false));
      return;
    }

    final pos = await Geolocator.getCurrentPosition(desiredAccuracy: LocationAccuracy.high);
    final elapsed = DateTime.now().millisecondsSinceEpoch - start;
    Navigator.pop(context, TestResult('Location Test', elapsed, '${pos.latitude},${pos.longitude}', true));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(body: const Center(child: Text('Getting location...')));
  }
}
