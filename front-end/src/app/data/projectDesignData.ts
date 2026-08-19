const MINIO_PUBLIC_BASE_URL =
  import.meta.env.VITE_MINIO_PUBLIC_BASE_URL ?? "http://localhost:9005/figma-designs";

export interface MinioPageLink {
  fileLink?: string | null;
}

export const buildMinioUrl = (path?: string | null) => {
  if (!path) {
    return "";
  }
  const trimmed = path.startsWith("/") ? path.slice(1) : path;
  return `${MINIO_PUBLIC_BASE_URL}/${trimmed}`;
};

const parseFileLink = (fileLink?: string | null) => {
  if (!fileLink) {
    return null;
  }

  const trimmed = fileLink.startsWith("/") ? fileLink.slice(1) : fileLink;
  const parts = trimmed.split("/");
  if (parts.length < 4 || parts[1] !== "pages") {
    return null;
  }

  return {
    projectName: parts[0],
    pageFolder: parts[2],
  };
};

export const getDesignPngUrl = (page: MinioPageLink) => {
  const parsed = parseFileLink(page.fileLink);
  if (!parsed) {
    return "";
  }
  return buildMinioUrl(`/${parsed.projectName}/pages/${parsed.pageFolder}/page-export.png`);
};

export const getWebScreenshotUrl = (page: MinioPageLink) => {
  const parsed = parseFileLink(page.fileLink);
  if (!parsed) {
    return "";
  }
  return buildMinioUrl(`/${parsed.projectName}/web-pages/${parsed.pageFolder}/screenshot.png`);
};
