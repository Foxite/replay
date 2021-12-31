import 'package:easy_localization/easy_localization.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_speed_dial/flutter_speed_dial.dart';
import 'package:path_provider/path_provider.dart';
import 'package:replay/replay/replay_list_widgets.dart';
import 'package:path/path.dart';
import 'package:replay/settings_page.dart';

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
      theme: ThemeData(brightness: Brightness.light),
      darkTheme: ThemeData(brightness: Brightness.dark),
      themeMode: ThemeMode.system,
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
  ValueNotifier<bool> isDialOpen = ValueNotifier(false);
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('TITLE'.tr()),
        actions: [
          IconButton(onPressed: () async {
            await platform.invokeMethod('startReplayForegroundService', {
              'NOTIFICATION_TITLE': 'NOTIFICATION_TITLE'.tr(),
              'NOTIFICATION_TEXT': 'NOTIFICATION_TEXT'.tr(),
              'SAVE_REPLAY_TEXT': 'SAVE_REPLAY_TEXT'.tr(),
              'TURN_OFF_TEXT': 'TURN_OFF_TEXT'.tr(),
              'SAVE_COMPLETE_TITLE': 'SAVE_COMPLETE_TITLE'.tr(),
              'SAVE_COMPLETE_TEXT': 'SAVE_COMPLETE_TEXT'.tr(),
              'PERMISSION_REQUIRED': 'PERMISSION_REQUIRED'.tr()
            });
          }, icon: const Icon(Icons.mic)),
          IconButton(onPressed: () async {
            await platform.invokeMethod('stopReplayForegroundService');
          }, icon: const Icon(Icons.mic_off)),
          IconButton(onPressed: () async {
            await platform.invokeMethod('saveReplay', {
              'PATH': join((await getApplicationDocumentsDirectory()).path, "./replays/")
            });
          }, icon: const Icon(Icons.save))
        ],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
      floatingActionButton: SpeedDial(
        icon: Icons.menu,
        activeIcon: Icons.close,
        spacing: 4,
        openCloseDial: isDialOpen,
        spaceBetweenChildren: 4,
        elevation: 8.0,
        animationSpeed: 200,
        children: [
          SpeedDialChild(
            child: const Icon(Icons.settings),
            label: "SETTINGS_LABEL".tr(),
            onTap: () {
              Navigator.push(context, MaterialPageRoute(builder: (context) => const SettingsPage()));
            }
          ),
          SpeedDialChild(
            child: const Icon(Icons.refresh),
            label: "REFRESH_LIST_LABEL".tr(),
            onTap: () {
              setState(() {});
            }
          )
        ],
      ),
      body: Container(
        constraints: const BoxConstraints.expand(),
        child: ReplayList(),
      ),
    );
  }
}