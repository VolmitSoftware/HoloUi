package art.arcane.holoui.board;

import com.google.gson.annotations.SerializedName;

public enum BoardFollowRotation {
  @SerializedName("fixed")
  FIXED,
  @SerializedName("yaw")
  YAW,
  @SerializedName("full")
  FULL
}
