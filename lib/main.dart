import 'package:easy_localization/easy_localization.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:replay/replay/replay_list_widgets.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await EasyLocalization.ensureInitialized();
  runApp(
    EasyLocalization(child: const ReplayApp(), 
    supportedLocales: const [Locale('en', 'US'), Locale('ko', 'KR')], 
    path: 'assets/translations',
    fallbackLocale: const Locale('en', 'US')
  ));
}

class ReplayApp extends StatelessWidget {
  const ReplayApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      localizationsDelegates: context.localizationDelegates,
      supportedLocales: context.supportedLocales,
      locale: context.locale,
      home: const MainPage(),
    );
  }
}

class MainPage extends StatefulWidget {
  const MainPage({Key? key}) : super(key: key);

  @override
  MainPageState createState() => MainPageState();
}

class MainPageState extends State<MainPage> {
  static const platform = MethodChannel('replay/replay.channel'); 
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('TITLE'.tr()),
        actions: [
          IconButton(onPressed: () async {
            await platform.invokeMethod('startReplayForegroundService', {
              'NOTIFICATION_TITLE': 'NOTIFICATION_TITLE'.tr(),
              'NOTIFICATION_TEXT': 'NOTIFICATION_TEXT'.tr()
            });
          }, icon: const Icon(Icons.mic))
        ],
      ),
      body: Container(
        constraints: const BoxConstraints.expand(),
        child: ReplayList(),
      ),
    );
  }
}