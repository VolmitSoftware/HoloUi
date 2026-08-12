package art.arcane.holoui.editor.sync;

import art.arcane.holoui.config.HuiSettings;

interface EditorSyncSettingsView {
  boolean enabled();

  String endpoint();

  String createToken();

  int sessionMinutes();

  int pollSeconds();

  int maximumProjectBytes();

  String builderUrl();

  static EditorSyncSettingsView runtime() {
    return new EditorSyncSettingsView() {
      @Override
      public boolean enabled() {
        return HuiSettings.editorSyncEnabled();
      }

      @Override
      public String endpoint() {
        return HuiSettings.editorSyncEndpoint();
      }

      @Override
      public String createToken() {
        return HuiSettings.editorSyncCreateToken();
      }

      @Override
      public int sessionMinutes() {
        return HuiSettings.editorSyncSessionMinutes();
      }

      @Override
      public int pollSeconds() {
        return HuiSettings.editorSyncPollSeconds();
      }

      @Override
      public int maximumProjectBytes() {
        return HuiSettings.editorSyncMaxProjectBytes();
      }

      @Override
      public String builderUrl() {
        return HuiSettings.builderUrl();
      }
    };
  }
}
