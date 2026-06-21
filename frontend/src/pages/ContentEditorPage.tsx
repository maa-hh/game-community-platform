import { ChangeEvent, FormEvent, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArticlePayload, ContentCategory, contentApi } from "../api/content";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

const MAX_ARTICLE_IMAGE_COUNT = 10;
const MAX_ARTICLE_IMAGE_SIZE = 2 * 1024 * 1024;
const MAX_CONTENT_LENGTH = 800;

function parseDateTimeLocalValue(value: string) {
  if (!value) {
    return null;
  }
  const matched = value.match(
    /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/
  );
  if (!matched) {
    return null;
  }
  const [, year, month, day, hour, minute, second = "00"] = matched;
  const date = new Date(
    Number(year),
    Number(month) - 1,
    Number(day),
    Number(hour),
    Number(minute),
    Number(second)
  );
  return Number.isNaN(date.getTime()) ? null : date;
}

function toDateTimeLocal(value?: string) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function fromDateTimeLocal(value: string) {
  const date = parseDateTimeLocalValue(value);
  if (!date) {
    return null;
  }
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

export function ContentEditorPage() {
  const { articleId } = useParams();
  const navigate = useNavigate();
  const editing = Boolean(articleId);
  const [categories, setCategories] = useState<ContentCategory[]>([]);
  const [title, setTitle] = useState("");
  const [summary, setSummary] = useState("");
  const [paragraphs, setParagraphs] = useState<string[]>([""]);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [categoryId, setCategoryId] = useState("");
  const [scheduledPublishTime, setScheduledPublishTime] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<"draft" | "publish" | "upload" | null>(null);
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);

  useEffect(() => {
    void bootstrap();
  }, [articleId]);

  async function bootstrap() {
    setLoading(true);
    setNotice(null);
    try {
      const categoryData = await contentApi.listCategories();
      setCategories(categoryData);
      if (editing && articleId) {
        const detail = await contentApi.getArticleDetail(Number(articleId));
        const articleContent = detail.content ? null : await contentApi.getArticleContent(Number(articleId));
        setTitle(detail.title ?? "");
        setSummary(detail.summary ?? "");
        setParagraphs(toParagraphs(detail.contentParagraphs ?? articleContent?.contentParagraphs, detail.content ?? articleContent?.content));
        setImageUrls(normalizeImages(detail.imageUrls ?? articleContent?.imageUrls ?? [], detail.coverUrl));
        setCategoryId(detail.categoryId ? String(detail.categoryId) : "");
        setScheduledPublishTime(toDateTimeLocal(detail.scheduledPublishTime));
      }
    } catch (err) {
      setNotice({ type: "error", text: friendlyError(err) });
    } finally {
      setLoading(false);
    }
  }

  const contentText = useMemo(() => joinParagraphs(paragraphs), [paragraphs]);
  const canSubmit = useMemo(() => Boolean(title.trim() && contentText.trim() && contentText.length <= MAX_CONTENT_LENGTH && categoryId), [title, contentText, categoryId]);

  async function handleUpload(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? []);
    if (files.length === 0) {
      return;
    }
    if (imageUrls.length + files.length > MAX_ARTICLE_IMAGE_COUNT) {
      setNotice({ type: "error", text: "文章图片最多支持10张，请先移除多余图片。" });
      event.target.value = "";
      return;
    }
    const oversized = files.find((file) => file.size > MAX_ARTICLE_IMAGE_SIZE);
    if (oversized) {
      setNotice({ type: "error", text: `「${oversized.name}」超过2MB，单张图片大小不能超过2MB。` });
      event.target.value = "";
      return;
    }
    setBusy("upload");
    setNotice({ type: "info", text: "封面图片上传中..." });
    try {
      const urls = await contentApi.uploadFiles(files);
      setImageUrls((current) => normalizeImages(current.concat(urls)));
      setNotice({ type: "success", text: "封面图片已上传，第一张会作为文章卡片封面。" });
    } catch (err) {
      setNotice({ type: "error", text: friendlyError(err) });
    } finally {
      setBusy(null);
      event.target.value = "";
    }
  }

  async function submit(status: 0 | 1) {
    if (!title.trim() || !contentText.trim() || !categoryId) {
      setNotice({ type: "error", text: "标题、正文和分类都需要填写。" });
      return;
    }
    if (contentText.length > MAX_CONTENT_LENGTH) {
      setNotice({ type: "error", text: "正文最多800字，请精简后再提交。" });
      return;
    }
    if (status === 1 && scheduledPublishTime) {
      const scheduledDate = parseDateTimeLocalValue(scheduledPublishTime);
      if (!scheduledDate) {
        setNotice({ type: "error", text: "定时发布时间格式不正确，请重新选择。" });
        return;
      }
      if (scheduledDate.getTime() < Date.now() - 60_000) {
        setNotice({ type: "error", text: "定时发布时间不能早于当前时间。" });
        return;
      }
    }
    setBusy(status === 0 ? "draft" : "publish");
    setNotice(null);
    const payload: ArticlePayload = {
      title: title.trim(),
      summary: summary.trim() || undefined,
      content: contentText,
      contentParagraphs: toParagraphMap(paragraphs),
      coverUrl: imageUrls[0],
      imageUrls: normalizeImages(imageUrls),
      categoryId: Number(categoryId),
      status,
      scheduledPublishTime: status === 1 ? fromDateTimeLocal(scheduledPublishTime) : null
    };
    try {
      const id = editing && articleId ? await contentApi.updateArticle(Number(articleId), payload) : await contentApi.createArticle(payload);
      setNotice({
        type: "success",
        text: status === 0 ? "草稿已保存。" : "内容已提交，进入审核流程。"
      });
      navigate(`/app/modules/content/${id}`);
    } catch (err) {
      setNotice({ type: "error", text: friendlyError(err) });
    } finally {
      setBusy(null);
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void submit(1);
  }

  return (
    <section className="page-view">
      <div className="page-heading compact-heading">
        <p className="eyebrow">{editing ? "编辑文章" : "新建文章"}</p>
        <h1>{editing ? "继续把这篇内容打磨完。" : "写一篇能进广场的内容。"}</h1>
        <span>草稿不会触发审核；点击发布后，文章会进入 `PENDING` 审核，再由异步任务决定是否公开。</span>
      </div>

      <div className="content-detail-actions">
        <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content")}>
          返回广场
        </ActionButton>
        <ActionButton variant="soft" onClick={() => navigate("/app/modules/content/mine")}>
          我的文章
        </ActionButton>
      </div>

      <form className="form-card content-editor" onSubmit={handleSubmit}>
        {notice && <Notice type={notice.type}>{notice.text}</Notice>}
        {loading && <Notice>正在加载编辑器数据...</Notice>}

        <label>
          标题
          <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={80} />
          <small>{title.length}/80</small>
        </label>

        <label>
          摘要
          <textarea value={summary} onChange={(event) => setSummary(event.target.value)} maxLength={200} />
          <small>{summary.length}/200</small>
        </label>

        <label>
          分类
          <select value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="">请选择分类</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          定时发布时间
          <input type="datetime-local" value={scheduledPublishTime} onChange={(event) => setScheduledPublishTime(event.target.value)} />
        </label>

        <label>
          正文
          <div className="paragraph-editor">
            {paragraphs.map((paragraph, index) => (
              <div key={index} className="paragraph-row">
                <textarea
                  className="content-editor-body"
                  value={paragraph}
                  maxLength={MAX_CONTENT_LENGTH}
                  onChange={(event) => setParagraphs((current) => current.map((item, itemIndex) => itemIndex === index ? event.target.value : item))}
                  placeholder={`第 ${index + 1} 段`}
                />
                <button
                  type="button"
                  onClick={() => setParagraphs((current) => current.length <= 1 ? [""] : current.filter((_, itemIndex) => itemIndex !== index))}
                >
                  删除本段
                </button>
              </div>
            ))}
            <div className="paragraph-actions">
              <button type="button" onClick={() => setParagraphs((current) => current.concat(""))}>
                添加段落
              </button>
              <small className={contentText.length > MAX_CONTENT_LENGTH ? "danger" : ""}>{contentText.length}/{MAX_CONTENT_LENGTH}</small>
            </div>
          </div>
        </label>

        <div className="content-uploader">
          <label className="upload-zone">
            {busy === "upload" ? "上传中..." : "上传封面图片"}
            <input type="file" multiple accept="image/*" onChange={handleUpload} disabled={busy === "upload"} />
          </label>
          <span className="upload-hint">
            所有上传图片都会作为文章封面轮播图，最多10张，单张不超过2MB；第一张会作为广场卡片封面。
          </span>
        </div>

        {imageUrls.length > 0 && (
          <div className="editor-image-grid">
            {imageUrls.map((image, index) => (
              <div key={image} className="editor-image-card">
                <img src={image} alt="已上传图片" />
                <span>{index === 0 ? "主封面" : `封面 ${index + 1}`}</span>
                <ActionButton
                  type="button"
                  variant="ghost"
                  onClick={() => setImageUrls((current) => current.filter((item) => item !== image))}
                >
                  移除
                </ActionButton>
              </div>
            ))}
          </div>
        )}

        <div className="content-editor-actions">
          <ActionButton
            type="button"
            variant="soft"
            busy={busy === "draft"}
            onClick={() => void submit(0)}
          >
            保存草稿
          </ActionButton>
          <ActionButton type="submit" busy={busy === "publish"} disabled={!canSubmit}>
            提交审核
          </ActionButton>
        </div>
      </form>
    </section>
  );
}

function normalizeImages(images: string[], coverUrl?: string) {
  const result: string[] = [];
  for (const image of images) {
    if (image && !result.includes(image)) {
      result.push(image);
    }
  }
  if (coverUrl && !result.includes(coverUrl)) {
    result.unshift(coverUrl);
  }
  return result.slice(0, MAX_ARTICLE_IMAGE_COUNT);
}

function joinParagraphs(paragraphs: string[]) {
  return paragraphs.map((item) => item.trim()).filter(Boolean).join("\n\n");
}

function toParagraphMap(paragraphs: string[]) {
  return Object.fromEntries(
    paragraphs
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item, index) => [`p${index + 1}`, item])
  );
}

function toParagraphs(contentParagraphs?: Record<string, string>, content?: string) {
  const values = contentParagraphs
    ? Object.entries(contentParagraphs)
      .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }))
      .map(([, value]) => value)
      .filter(Boolean)
    : [];
  if (values.length > 0) {
    return values;
  }
  if (content?.trim()) {
    return content.split(/\n{2,}/).map((item) => item.trim()).filter(Boolean);
  }
  return [""];
}
