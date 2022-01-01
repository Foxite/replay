import 'package:easy_localization/src/public_ext.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_settings_ui/flutter_settings_ui.dart';
import 'package:enum_to_string/enum_to_string.dart';
import 'package:replay/preferences.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({Key? key}) : super(key: key);

  @override
  SettingsPageState createState() => SettingsPageState();
}

class SettingsPageState extends State<SettingsPage> {
  static const platform = MethodChannel('replay/replay.channel');
  ReplaySettingsList? settingsWidget;
  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('SETTINGS_LABEL'.tr())),
      body: FutureBuilder<Map<String, dynamic>>(
        future: getPreferences(),
        builder: (context, snapshot) {
          if (snapshot.data == null) {
            return Container(
              constraints: const BoxConstraints.expand(),
              alignment: Alignment.center,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Text('LOADING_SETTINGS'.tr()),
                  ),
                  const Padding(
                    padding: EdgeInsets.all(16.0),
                    child: LinearProgressIndicator(),
                  )
                ],
              ),
            );
          }
          settingsWidget = ReplaySettingsList(snapshot.data!);
          return settingsWidget!;
        },
      ),
      floatingActionButton: FloatingActionButton(
          child: const Icon(Icons.save),
          onPressed: () async {
            if (!((await platform
                .invokeMethod("isReplayForegroundServiceRunning")) as bool)) {
              await saveSettingsAndApply(false);
              Navigator.of(context).popUntil((route) => !route.isFirst);
              return;
            }
            showDialog(
              context: context,
              builder: (context) => AlertDialog(
                title: Text('SAVE_SETTINGS_ALERT_TITLE'.tr()),
                content: Text('SAVE_SETTINGS_ALERT_TEXT'.tr()),
                actions: [
                  TextButton(
                      onPressed: () async {
                        await saveSettingsAndApply(true);
                        Navigator.of(context)
                            .popUntil((route) => !route.isFirst);
                      },
                      child: Text("DIALOG_YES".tr())),
                  TextButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      child: Text("DIALOG_NO".tr()))
                ],
              ),
            );
          }),
    );
  }

  Future<Map<String, dynamic>> getPreferences() async {
    return await platform.invokeMapMethod<String, dynamic>(
        "getSettingPreferences") as Map<String, dynamic>;
  }

  Future<void> saveSettingsAndApply(bool needRestart) async {
    if (settingsWidget == null) return;
    if (needRestart) {
      await platform.invokeMethod("stopReplayForegroundService");
    }
    Map<String, dynamic> preferences = settingsWidget!.changedPreferences;
    await platform.invokeMethod("updateSettingPreferences", preferences);
    if (needRestart) {
      await platform.invokeMethod("startReplayForegroundService");
    }
  }
}

class ReplaySettingsList extends StatefulWidget {
  final Map<String, dynamic> preferences;
  final Map<String, dynamic> changedPreferences = {};
  ReplaySettingsList(this.preferences, {Key? key}) : super(key: key);
  @override
  ReplaySettingsListState createState() => ReplaySettingsListState();
}

class ReplaySettingsListState extends State<ReplaySettingsList> {
  Map<BoolPreference, bool> boolPreferences = {};
  Map<IntPreference, int> intPreferences = {};

  @override
  void initState() {
    super.initState();
    Map<String, dynamic> preferences = widget.preferences;
    for (String key in preferences.keys) {
      BoolPreference? boolKey =
          EnumToString.fromString(BoolPreference.values, key);
      IntPreference? intKey =
          EnumToString.fromString(IntPreference.values, key);
      if (boolKey != null) {
        boolPreferences[boolKey] = preferences[key] as bool;
      } else if (intKey != null) {
        intPreferences[intKey] = preferences[key] as int;
      }
    }
    for (BoolPreference key in BoolPreference.values) {
      if (boolPreferences[key] == null) {
        boolPreferences[key] = false;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    widget.changedPreferences
        .addAll(boolPreferences.map((key, value) => MapEntry(key.name, value)));
    widget.changedPreferences
        .addAll(intPreferences.map((key, value) => MapEntry(key.name, value)));
    return SettingsList(
      contentPadding: const EdgeInsets.fromLTRB(0, 8, 0, 0),
      backgroundColor: const Color.fromARGB(0, 0, 0, 0),
      sections: [
        SettingsSection(
          title: 'SETTINGS_SECTION_CHANNELS'.tr(),
          tiles: [
            SettingsTile.switchTile(
              title: 'SETTINGS_CHANNEL_MIC'.tr(),
              leading: const Icon(Icons.mic),
              switchValue: boolPreferences[BoolPreference.USE_MIC_CHANNEL],
              onToggle: (value) {
                setState(() {
                  boolPreferences[BoolPreference.USE_MIC_CHANNEL] = value;
                });
              },
            ),
            SettingsTile.switchTile(
              title: 'SETTINGS_CHANNEL_OUTPUT'.tr(),
              leading: const Icon(Icons.speaker),
              switchValue: boolPreferences[BoolPreference.USE_OUTPUT_CHANNEL],
              onToggle: (value) {
                setState(() {
                  boolPreferences[BoolPreference.USE_OUTPUT_CHANNEL] = value;
                });
              },
            )
          ],
        ),
        SettingsSection(
          title: 'SETTINGS_SECTION_SAVE'.tr(),
          tiles: [
            SettingsTile(
              title: 'SETTINGS_SAVE_RECORD_LENGTH'.tr(),
              subtitle: intPreferences[IntPreference.RECORD_LENGTH].toString(),
              leading: const Icon(Icons.timelapse),
              onPressed: (context) {
                showIntEditDialog(context, IntPreference.RECORD_LENGTH,
                    'SETTINGS_SAVE_RECORD_LENGTH');
              },
            ),
            SettingsTile.switchTile(
              title: 'SETTINGS_SAVE_SEPERATELY_BY_CHANNEL'.tr(),
              leading: const Icon(Icons.file_copy),
              switchValue:
                  boolPreferences[BoolPreference.SAVE_SEPERATELY_BY_CHANNEL],
              onToggle: (value) {
                setState(() {
                  boolPreferences[BoolPreference.SAVE_SEPERATELY_BY_CHANNEL] =
                      value;
                });
              },
            )
          ],
        ),
      ],
    );
  }

  void showIntEditDialog(
      BuildContext context, IntPreference key, String titleKey) {
    TextEditingController controller =
        TextEditingController(text: intPreferences[key].toString());
    showDialog(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text(titleKey.tr()),
            content: TextField(
              controller: controller,
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.digitsOnly
              ],
            ),
            actions: [
              TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                    setState(() {
                      intPreferences[key] = int.parse(controller.text);
                    });
                  },
                  child: Text("DIALOG_CONFIRM".tr())),
              TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                  },
                  child: Text("DIALOG_CANCEL".tr()))
            ],
          );
        });
  }
}
