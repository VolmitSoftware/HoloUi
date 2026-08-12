package art.arcane.holoui.editor.sync;

import com.google.gson.JsonObject;

import java.util.Objects;

record EditorSyncPendingAck(long publicationRevision, String status, String message,
                            JsonObject serverProject) {
  EditorSyncPendingAck {
    if (publicationRevision < 1L) {
      throw new IllegalArgumentException("publicationRevision must be positive");
    }
    if (!Objects.equals(status, "applied") && !Objects.equals(status, "conflict")
        && !Objects.equals(status, "rejected")) {
      throw new IllegalArgumentException("invalid editor sync acknowledgement status");
    }
    if (message == null || message.isBlank() || message.length() > 1000) {
      throw new IllegalArgumentException("acknowledgement message is invalid");
    }
    if ((status.equals("applied") || status.equals("conflict")) && serverProject == null) {
      throw new IllegalArgumentException("applied/conflict acknowledgement requires a server project");
    }
    if (status.equals("rejected") && serverProject != null) {
      throw new IllegalArgumentException("rejected acknowledgement cannot include a server project");
    }
    serverProject = serverProject == null ? null : serverProject.deepCopy();
  }

  EditorSyncProject validatedServerProject(int maximumBytes) {
    return serverProject == null ? null : EditorSyncProject.validated(serverProject, maximumBytes);
  }
}
