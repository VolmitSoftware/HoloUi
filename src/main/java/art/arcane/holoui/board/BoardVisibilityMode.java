package art.arcane.holoui.board;

import com.google.gson.annotations.SerializedName;

public enum BoardVisibilityMode {
  @SerializedName("public")
  PUBLIC,
  @SerializedName("permission")
  PERMISSION,
  @SerializedName("hidden")
  HIDDEN
}
