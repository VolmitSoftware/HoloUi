package art.arcane.holoui.board;

public final class BoardRevisionConflictException extends IllegalStateException {
  private final String boardId;
  private final long expectedRevision;
  private final long actualRevision;

  public BoardRevisionConflictException(String boardId, long expectedRevision, long actualRevision) {
    super("board " + boardId + " revision conflict: expected " + expectedRevision + ", actual " + actualRevision);
    this.boardId = boardId;
    this.expectedRevision = expectedRevision;
    this.actualRevision = actualRevision;
  }

  public String boardId() {
    return boardId;
  }

  public long expectedRevision() {
    return expectedRevision;
  }

  public long actualRevision() {
    return actualRevision;
  }
}
