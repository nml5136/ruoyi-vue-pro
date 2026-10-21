package cn.iocoder.yudao.module.ilvy.controller.app;

import java.util.*;
import javax.validation.Valid;
import javax.validation.constraints.*;
import lombok.Data;

public class IlvyModels {
  @Data
  public static class Query {
    @Size(max = 100)
    private String q = "";

    @Size(max = 30)
    private String province = "", city = "", district = "", type = "", theme = "", level = "";

    @NotNull
    @Size(max = 10)
    private List<@NotBlank @Size(max = 30) String> types = new ArrayList<>();

    @NotNull
    @Size(max = 10)
    private List<@NotBlank @Size(max = 30) String> themes = new ArrayList<>();

    @NotNull
    @Size(max = 10)
    private List<@NotBlank @Size(max = 30) String> levels = new ArrayList<>();

    @Min(1)
    @Max(1440)
    private Integer maxDrive;

    private Boolean free, stroller, booking, favoriteOnly;

    @Min(0)
    @Max(10000)
    private Integer budget;

    @Min(0)
    @Max(18)
    private Integer age;

    @Min(1)
    @Max(72)
    private Integer maxStay;
  }

  @Data
  @lombok.experimental.Accessors(chain = true)
  public static class CatalogCollection {
    private String id,
        parentId,
        kind,
        name,
        province = "",
        city = "",
        district = "",
        theme = "",
        title,
        intro,
        season,
        reviewStatus = "pending",
        source;
    private Integer recommendedDays;
    private int placeCount;
    private boolean sampleOnly;
    private List<String> placeIds = new ArrayList<>(), themes = new ArrayList<>();
  }

  @Data
  public static class PlanItem {
    @NotBlank
    @Size(max = 80)
    private String placeId;

    @NotNull
    @Size(max = 500)
    private String note = "";
  }

  @Data
  public static class Favorite {
    @NotBlank
    @Size(max = 80)
    private String placeId;

    @NotNull
    @Size(max = 40)
    private String group = "默认分组";
  }

  @Data
  public static class Preferences {
    @NotNull
    @Size(max = 40)
    private String origin = "";

    @Min(1)
    @Max(1440)
    private Integer maxDrive;

    @Min(0)
    @Max(18)
    private Integer childAge;

    @Min(1)
    @Max(72)
    private Integer duration;

    @NotNull
    @Size(max = 10)
    private List<@NotBlank @Size(max = 20) String> interests = new ArrayList<>();
  }

  @Data
  public static class State {
    @Min(0)
    private long version;

    @Valid
    @NotNull
    @Size(max = 200)
    private List<@NotNull Favorite> favorites = new ArrayList<>();

    @Valid
    @NotNull
    @Size(max = 50)
    private List<@NotNull PlanItem> plan = new ArrayList<>();

    @Size(max = 60)
    @NotNull
    private String planName = "我的出游计划";

    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$")
    @NotNull
    private String planDate = "";

    @NotNull
    @Size(max = 3)
    private List<@NotBlank @Size(max = 80) String> compare = new ArrayList<>();

    @Valid @NotNull private Preferences preferences = new Preferences();
  }

  @Data
  public static class Feedback {
    @NotBlank
    @Size(max = 80)
    private String placeId;

    @NotBlank
    @Pattern(regexp = "开放时间|位置入口|费用|设施|内容|其他")
    private String category;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @NotNull
    @Size(max = 3)
    private List<@NotBlank @Pattern(regexp = "[0-9a-f-]{36}") String> evidenceIds =
        new ArrayList<>();
  }

  @Data
  @lombok.experimental.Accessors(chain = true)
  public static class EvidenceRecord {
    private String id, contentType, filePath, feedbackId;
    private long size;
  }
}
