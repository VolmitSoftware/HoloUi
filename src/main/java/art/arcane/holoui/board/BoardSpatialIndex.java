package art.arcane.holoui.board;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class BoardSpatialIndex {
  private static final double CHUNK_SIZE = 16.0D;

  private final Map<UUID, BoardDefinition> boardsByUuid;
  private final Map<String, UUID> uuidsById;
  private final Map<UUID, Map<ChunkCoordinate, Set<UUID>>> chunksByWorld;
  private final ReentrantReadWriteLock lock;

  public BoardSpatialIndex() {
    boardsByUuid = new HashMap<>();
    uuidsById = new HashMap<>();
    chunksByWorld = new HashMap<>();
    lock = new ReentrantReadWriteLock();
  }

  public int size() {
    lock.readLock().lock();
    try {
      return boardsByUuid.size();
    } finally {
      lock.readLock().unlock();
    }
  }

  public Optional<BoardDefinition> get(String id) {
    String canonicalId = BoardIds.canonicalize(id);
    lock.readLock().lock();
    try {
      UUID boardUuid = uuidsById.get(canonicalId);
      return boardUuid == null ? Optional.empty() : Optional.ofNullable(boardsByUuid.get(boardUuid));
    } finally {
      lock.readLock().unlock();
    }
  }

  public Optional<BoardDefinition> get(UUID boardUuid) {
    UUID requiredUuid = Objects.requireNonNull(boardUuid, "boardUuid");
    lock.readLock().lock();
    try {
      return Optional.ofNullable(boardsByUuid.get(requiredUuid));
    } finally {
      lock.readLock().unlock();
    }
  }

  public List<BoardDefinition> list() {
    lock.readLock().lock();
    try {
      List<BoardDefinition> snapshot = new ArrayList<>(boardsByUuid.values());
      snapshot.sort((left, right) -> left.id().compareTo(right.id()));
      return List.copyOf(snapshot);
    } finally {
      lock.readLock().unlock();
    }
  }

  public void replaceAll(Collection<BoardDefinition> boards) {
    Objects.requireNonNull(boards, "boards");
    Map<UUID, BoardDefinition> replacementBoards = new HashMap<>(capacityFor(boards.size()));
    Map<String, UUID> replacementIds = new HashMap<>(capacityFor(boards.size()));
    Map<UUID, Map<ChunkCoordinate, Set<UUID>>> replacementChunks = new HashMap<>();

    for (BoardDefinition board : boards) {
      BoardDefinition requiredBoard = Objects.requireNonNull(board, "boards must not contain null");
      if (replacementBoards.putIfAbsent(requiredBoard.uuid(), requiredBoard) != null) {
        throw new IllegalArgumentException("duplicate board UUID: " + requiredBoard.uuid());
      }
      UUID previousIdOwner = replacementIds.putIfAbsent(requiredBoard.id(), requiredBoard.uuid());
      if (previousIdOwner != null) {
        throw new IllegalArgumentException("duplicate board ID: " + requiredBoard.id());
      }
      addToChunk(replacementChunks, requiredBoard);
    }

    lock.writeLock().lock();
    try {
      boardsByUuid.clear();
      boardsByUuid.putAll(replacementBoards);
      uuidsById.clear();
      uuidsById.putAll(replacementIds);
      chunksByWorld.clear();
      chunksByWorld.putAll(replacementChunks);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public void upsert(BoardDefinition board) {
    BoardDefinition requiredBoard = Objects.requireNonNull(board, "board");
    lock.writeLock().lock();
    try {
      UUID idOwner = uuidsById.get(requiredBoard.id());
      if (idOwner != null && !idOwner.equals(requiredBoard.uuid())) {
        throw new IllegalArgumentException("duplicate board ID: " + requiredBoard.id());
      }

      BoardDefinition previous = boardsByUuid.put(requiredBoard.uuid(), requiredBoard);
      if (previous != null) {
        removeFromChunk(previous);
        uuidsById.remove(previous.id());
      }
      uuidsById.put(requiredBoard.id(), requiredBoard.uuid());
      addToChunk(chunksByWorld, requiredBoard);
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean remove(UUID boardUuid) {
    UUID requiredUuid = Objects.requireNonNull(boardUuid, "boardUuid");
    lock.writeLock().lock();
    try {
      BoardDefinition removed = boardsByUuid.remove(requiredUuid);
      if (removed == null) {
        return false;
      }
      uuidsById.remove(removed.id());
      removeFromChunk(removed);
      return true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  public List<BoardDefinition> query(UUID worldUuid, double x, double z, double radius) {
    UUID requiredWorldUuid = Objects.requireNonNull(worldUuid, "worldUuid");
    requireFinite(x, "x");
    requireFinite(z, "z");
    requireRadius(radius);

    lock.readLock().lock();
    try {
      Map<ChunkCoordinate, Set<UUID>> worldChunks = chunksByWorld.get(requiredWorldUuid);
      if (worldChunks == null || worldChunks.isEmpty()) {
        return List.of();
      }

      int minimumChunkX = chunkCoordinate(x - radius);
      int maximumChunkX = chunkCoordinate(x + radius);
      int minimumChunkZ = chunkCoordinate(z - radius);
      int maximumChunkZ = chunkCoordinate(z + radius);
      Set<UUID> candidateUuids = collectCandidates(worldChunks, minimumChunkX, maximumChunkX,
          minimumChunkZ, maximumChunkZ);
      List<BoardDefinition> matches = new ArrayList<>(candidateUuids.size());
      for (UUID candidateUuid : candidateUuids) {
        BoardDefinition board = boardsByUuid.get(candidateUuid);
        if (board != null && horizontalDistance(board, x, z) <= radius) {
          matches.add(board);
        }
      }
      matches.sort((left, right) -> compare(left, right, x, z));
      return List.copyOf(matches);
    } finally {
      lock.readLock().unlock();
    }
  }

  private static int capacityFor(int size) {
    if (size < 3) {
      return size + 1;
    }
    return (int) Math.min(Integer.MAX_VALUE, (long) Math.ceil(size / 0.75D));
  }

  private static void addToChunk(Map<UUID, Map<ChunkCoordinate, Set<UUID>>> chunks, BoardDefinition board) {
    BoardTransform transform = board.transform();
    ChunkCoordinate coordinate = ChunkCoordinate.from(transform.x(), transform.z());
    chunks.computeIfAbsent(transform.worldUuid(), ignored -> new HashMap<>())
        .computeIfAbsent(coordinate, ignored -> new HashSet<>())
        .add(board.uuid());
  }

  private static Set<UUID> collectCandidates(Map<ChunkCoordinate, Set<UUID>> worldChunks,
                                              int minimumChunkX, int maximumChunkX,
                                              int minimumChunkZ, int maximumChunkZ) {
    long width = (long) maximumChunkX - minimumChunkX + 1L;
    long depth = (long) maximumChunkZ - minimumChunkZ + 1L;
    if (width > worldChunks.size() || depth > worldChunks.size()
        || width * depth > worldChunks.size()) {
      return collectExistingBuckets(worldChunks, minimumChunkX, maximumChunkX, minimumChunkZ, maximumChunkZ);
    }

    Set<UUID> candidates = new HashSet<>();
    for (long chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
      for (long chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
        Set<UUID> bucket = worldChunks.get(new ChunkCoordinate((int) chunkX, (int) chunkZ));
        if (bucket != null) {
          candidates.addAll(bucket);
        }
      }
    }
    return candidates;
  }

  private static Set<UUID> collectExistingBuckets(Map<ChunkCoordinate, Set<UUID>> worldChunks,
                                                   int minimumChunkX, int maximumChunkX,
                                                   int minimumChunkZ, int maximumChunkZ) {
    Set<UUID> candidates = new HashSet<>();
    for (Map.Entry<ChunkCoordinate, Set<UUID>> entry : worldChunks.entrySet()) {
      ChunkCoordinate coordinate = entry.getKey();
      if (coordinate.x() >= minimumChunkX && coordinate.x() <= maximumChunkX
          && coordinate.z() >= minimumChunkZ && coordinate.z() <= maximumChunkZ) {
        candidates.addAll(entry.getValue());
      }
    }
    return candidates;
  }

  private static int compare(BoardDefinition left, BoardDefinition right, double x, double z) {
    int distanceComparison = Double.compare(horizontalDistance(left, x, z), horizontalDistance(right, x, z));
    if (distanceComparison != 0) {
      return distanceComparison;
    }
    int idComparison = left.id().compareTo(right.id());
    if (idComparison != 0) {
      return idComparison;
    }
    return left.uuid().compareTo(right.uuid());
  }

  private static double horizontalDistance(BoardDefinition board, double x, double z) {
    BoardTransform transform = board.transform();
    return Math.hypot(transform.x() - x, transform.z() - z);
  }

  private static int chunkCoordinate(double blockCoordinate) {
    if (blockCoordinate <= (double) Integer.MIN_VALUE * CHUNK_SIZE) {
      return Integer.MIN_VALUE;
    }
    if (blockCoordinate >= (double) Integer.MAX_VALUE * CHUNK_SIZE) {
      return Integer.MAX_VALUE;
    }
    return (int) Math.floor(blockCoordinate / CHUNK_SIZE);
  }

  private static void requireFinite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
  }

  private static void requireRadius(double radius) {
    if (!Double.isFinite(radius) || radius < 0.0D) {
      throw new IllegalArgumentException("radius must be finite and non-negative");
    }
  }

  private void removeFromChunk(BoardDefinition board) {
    BoardTransform transform = board.transform();
    Map<ChunkCoordinate, Set<UUID>> worldChunks = chunksByWorld.get(transform.worldUuid());
    if (worldChunks == null) {
      return;
    }
    ChunkCoordinate coordinate = ChunkCoordinate.from(transform.x(), transform.z());
    Set<UUID> bucket = worldChunks.get(coordinate);
    if (bucket == null) {
      return;
    }
    bucket.remove(board.uuid());
    if (bucket.isEmpty()) {
      worldChunks.remove(coordinate);
    }
    if (worldChunks.isEmpty()) {
      chunksByWorld.remove(transform.worldUuid());
    }
  }

  private record ChunkCoordinate(int x, int z) {
    private static ChunkCoordinate from(double x, double z) {
      return new ChunkCoordinate(chunkCoordinate(x), chunkCoordinate(z));
    }
  }
}
