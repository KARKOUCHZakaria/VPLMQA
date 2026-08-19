const { chromium } = require("playwright");
const fs = require("fs");
const http = require("http");
const path = require("path");

async function main() {
  const video = process.argv[2];
  const out = process.argv[3] || "C:/tmp/vplmqa_video_frames";
  fs.mkdirSync(out, { recursive: true });
  const server = await startVideoServer(video);

  const launchOptions = { headless: true, args: ["--allow-file-access-from-files", "--autoplay-policy=no-user-gesture-required"] };
  if (process.env.PLAYWRIGHT_CHROME_EXECUTABLE) {
    launchOptions.executablePath = process.env.PLAYWRIGHT_CHROME_EXECUTABLE;
  }
  const browser = await chromium.launch(launchOptions);
  const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
  const fileUrl = `http://127.0.0.1:${server.address().port}/video.mp4`;

  await page.setContent(`
    <html>
      <body style="margin:0;background:#111;display:flex;align-items:center;justify-content:center;height:100vh">
        <video id="v" src="${fileUrl}" style="max-width:100vw;max-height:100vh" muted></video>
      </body>
    </html>
  `);
  await page.waitForFunction(() => {
    const videoElement = document.querySelector("video");
    return videoElement && (videoElement.readyState >= 1 || videoElement.error);
  }, { timeout: 15000 });
  const mediaError = await page.$eval("video", (v) => v.error ? { code: v.error.code, message: v.error.message } : null);
  if (mediaError) {
    throw new Error(`Video load error: ${JSON.stringify(mediaError)}`);
  }
  const duration = await page.$eval("video", (v) => v.duration);
  console.log("duration", duration);

  const times = [];
  const frameCount = Number.parseInt(process.argv[4] || "12", 10);
  for (let i = 0; i < frameCount; i += 1) {
    times.push(Math.max(0.1, (duration * (i + 0.5)) / frameCount));
  }

  for (let i = 0; i < times.length; i += 1) {
    await page.$eval("video", (v, t) => {
      window.__targetTime = t;
      v.currentTime = t;
    }, times[i]);
    await page.waitForFunction(() => {
      const v = document.querySelector("video");
      return Math.abs(v.currentTime - window.__targetTime) < 0.35 || v.readyState >= 2;
    }, { timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(350);
    const framePath = path.join(out, `frame_${String(i + 1).padStart(2, "0")}.png`);
    await page.screenshot({ path: framePath, fullPage: true });
    console.log(framePath);
  }
  await browser.close();
  server.close();
}

function startVideoServer(videoPath) {
  return new Promise((resolve) => {
    const server = http.createServer((request, response) => {
      const stat = fs.statSync(videoPath);
      const range = request.headers.range;
      if (range) {
        const parts = range.replace(/bytes=/, "").split("-");
        const start = parseInt(parts[0], 10);
        const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
        response.writeHead(206, {
          "Content-Range": `bytes ${start}-${end}/${stat.size}`,
          "Accept-Ranges": "bytes",
          "Content-Length": end - start + 1,
          "Content-Type": "video/mp4",
        });
        fs.createReadStream(videoPath, { start, end }).pipe(response);
        return;
      }
      response.writeHead(200, {
        "Content-Length": stat.size,
        "Content-Type": "video/mp4",
      });
      fs.createReadStream(videoPath).pipe(response);
    });
    server.listen(0, "127.0.0.1", () => resolve(server));
  });
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
