import 'package:easy_localization/src/public_ext.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_settings_ui/flutter_settings_ui.dart';

enum BoolPreferencesEnum {
  USE_MIC_CHANNEL, USE_OUTPUT_CHANNEL, SAVE_SEPERATELY_BY_CHANNEL
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({Key? key}) : super(key: key);

  @override
  SettingsPageState createState() => SettingsPageState();
}

class SettingsPageState extends State<SettingsPage> {
  Map<BoolPreferencesEnum, bool> boolPreferences = {};

  @override 
  void initState() {
    super.initState();
    for(BoolPreferencesEnum preference in BoolPreferencesEnum.values) {
      if(boolPreferences[preference] != null) continue;
      boolPreferences[preference] = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('SETTINGS_LABEL'.tr())),
      body: SettingsList(
        contentPadding: const EdgeInsets.fromLTRB(0, 8, 0, 0),
        backgroundColor: const Color.fromARGB(0, 0, 0, 0),
        sections: [
          SettingsSection(
            title: 'SETTINGS_SECTION_CHANNELS'.tr(),
            tiles: [
              SettingsTile.switchTile(
                title: 'SETTINGS_CHANNEL_MIC'.tr(),
                leading: const Icon(Icons.mic),
                switchValue: boolPreferences[BoolPreferencesEnum.USE_MIC_CHANNEL],
                onToggle: (value) {
                  setState(() {
                    boolPreferences[BoolPreferencesEnum.USE_MIC_CHANNEL] = value;
                  });
                },
              ),
              SettingsTile.switchTile(
                title: 'SETTINGS_CHANNEL_OUTPUT'.tr(),
                leading: const Icon(Icons.speaker),
                switchValue: boolPreferences[BoolPreferencesEnum.USE_OUTPUT_CHANNEL],
                onToggle: (value) {
                  setState(() {
                    boolPreferences[BoolPreferencesEnum.USE_OUTPUT_CHANNEL] = value;
                  });
                },
              )
            ],
          ),
          SettingsSection(
            title: 'SETTINGS_SECTION_SAVE'.tr(),
            tiles: [
              SettingsTile.switchTile(
                title: 'SETTINGS_SAVE_SEPERATELY_BY_CHANNEL'.tr(),
                leading: const Icon(Icons.file_copy),
                switchValue: boolPreferences[BoolPreferencesEnum.SAVE_SEPERATELY_BY_CHANNEL],
                onToggle: (value) {
                  setState(() {
                    boolPreferences[BoolPreferencesEnum.SAVE_SEPERATELY_BY_CHANNEL] = value;
                  });
                },
              )
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        child: const Icon(Icons.save),
        onPressed: () {

      }),
    );
  }
}