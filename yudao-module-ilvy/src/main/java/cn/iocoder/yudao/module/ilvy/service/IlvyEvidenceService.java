package cn.iocoder.yudao.module.ilvy.service;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.ilvy.controller.app.IlvyModels.EvidenceRecord;
import cn.iocoder.yudao.module.ilvy.dal.IlvyRepository;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IlvyEvidenceService {
  public static final long MAX_BYTES = 2 * 1024 * 1024;
  private final IlvyRepository repo;
  private final Path root;

  public IlvyEvidenceService(
      IlvyRepository repo, @Value("${ilvy.evidence-directory:}") String directory) {
    this.repo = repo;
    this.root =
        directory.trim().isEmpty() ? null : Paths.get(directory).toAbsolutePath().normalize();
    if (root != null
        && (root.getNameCount() < 2 || !root.getFileName().toString().startsWith("ilvy-evidence")))
      throw new IllegalArgumentException("Use a dedicated ilvy-evidence directory");
  }

  public boolean enabled() {
    return root != null;
  }

  private void requireEnabled() {
    if (!enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片证据存储尚未配置");
  }

  private static void privateDirectory(Path path) throws IOException {
    if (Files.isSymbolicLink(path)) throw new IOException("Symbolic evidence directory rejected");
    try {
      Files.createDirectories(
          path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    } catch (UnsupportedOperationException e) {
      Files.createDirectories(path);
    }
  }

  private String imageType(byte[] bytes) {
    boolean png =
        bytes.length >= 8
            && (bytes[0] & 255) == 137
            && bytes[1] == 80
            && bytes[2] == 78
            && bytes[3] == 71
            && bytes[4] == 13
            && bytes[5] == 10
            && bytes[6] == 26
            && bytes[7] == 10;
    boolean jpeg =
        bytes.length >= 3
            && (bytes[0] & 255) == 255
            && (bytes[1] & 255) == 216
            && (bytes[2] & 255) == 255;
    if (!png && !jpeg)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PNG/JPEG 图片，不接受改扩展名的其他文件");
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("Image unreadable");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || w > 8192 || h > 8192 || (long) w * h > 20000000)
          throw new IOException("Image dimensions exceeded");
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片无法读取或尺寸超出限制");
    }
    return png ? "image/png" : "image/jpeg";
  }

  @Transactional
  public Map<String, Object> upload(long user, MultipartFile file) {
    requireEnabled();
    repo.assertOwner(user);
    if (file == null || file.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择图片");
    if (file.getSize() > MAX_BYTES)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "每张证据图片最多 2MB");
    Path target = null;
    try {
      byte[] bytes = file.getBytes();
      String type = imageType(bytes), id = UUID.randomUUID().toString();
      String owner = SecurityFrameworkUtils.getLoginUser().getTenantId() + "-member-" + user;
      Path dir = root.resolve(owner);
      privateDirectory(root);
      privateDirectory(dir);
      target = dir.resolve(id + (type.equals("image/png") ? ".png" : ".jpg"));
      try {
        Files.createFile(
            target,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      } catch (UnsupportedOperationException e) {
        Files.createFile(target);
      }
      Files.write(target, bytes, StandardOpenOption.WRITE);
      EvidenceRecord row =
          new EvidenceRecord()
              .setId(id)
              .setContentType(type)
              .setFilePath(target.toString())
              .setSize(bytes.length);
      StringBuilder hash = new StringBuilder();
      for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
        hash.append(String.format("%02x", b & 255));
      repo.addEvidence(user, row, hash.toString());
      return info(row);
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      if (target != null)
        try {
          Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "证据图片未保存，请稍后重试");
    }
  }

  public EvidenceRecord get(long user, String id) {
    requireEnabled();
    EvidenceRecord row = repo.evidence(user, id);
    if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "证据不存在或无权访问");
    return row;
  }

  public Map<String, Object> info(EvidenceRecord row) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", row.getId());
    result.put("contentType", row.getContentType());
    result.put("size", row.getSize());
    return result;
  }

  private Path checkedPath(EvidenceRecord row) {
    Path path = Paths.get(row.getFilePath()).toAbsolutePath().normalize();
    if (!path.startsWith(root) || Files.isSymbolicLink(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "证据不可访问");
    return path;
  }

  public byte[] bytes(long user, String id) {
    EvidenceRecord row = get(user, id);
    try {
      Path path = checkedPath(row);
      if (Files.size(path) > MAX_BYTES) throw new IOException("File limit exceeded");
      return Files.readAllBytes(path);
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "证据文件暂不可读取");
    }
  }

  @Transactional
  public void remove(long user, String id) {
    EvidenceRecord row = get(user, id);
    if (row.getFeedbackId() != null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "已提交的证据保留在反馈记录中");
    try {
      if (repo.removeUnsubmittedEvidence(user, id) != 1)
        throw new ResponseStatusException(HttpStatus.CONFLICT, "证据状态已变化，请刷新");
      Files.deleteIfExists(checkedPath(row));
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "图片未移除，请重试");
    }
  }
}
