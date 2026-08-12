package art.arcane.holoui.config.menu;

public final class MenuRevisionConflictException extends IllegalStateException {
  private final String menuId;
  private final String expectedRevision;
  private final String actualRevision;

  public MenuRevisionConflictException(String menuId, String expectedRevision, String actualRevision) {
    super("menu '" + menuId + "' changed from revision " + expectedRevision + " to " + actualRevision);
    this.menuId = menuId;
    this.expectedRevision = expectedRevision;
    this.actualRevision = actualRevision;
  }

  public String menuId() {
    return menuId;
  }

  public String expectedRevision() {
    return expectedRevision;
  }

  public String actualRevision() {
    return actualRevision;
  }
}
