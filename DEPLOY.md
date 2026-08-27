# Deploy — QueueFlow

Architecture: **Netlify** (frontend) + **Render** (backend + PostgreSQL).

## 1. PostgreSQL on Render
1. Render dashboard → New → PostgreSQL. Note the **Internal Database URL** (or host/port/db/user/password) once created.

## 2. Backend Web Service on Render
1. New → Web Service → connect the repo (or use the included `render.yaml` as a Blueprint: New → Blueprint).
2. Environment: `Java`. Build command: `./mvnw clean package -DskipTests`. Start command: `java -jar target/queueflow-backend-1.0.0.jar`.
3. Root directory: `backend`.

## 3. Configure backend env vars
On the Render service, set:
- `SPRING_PROFILES_ACTIVE=prod`
- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<database>` (from step 1)
- `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` (from step 1)
- `JWT_SECRET` — a long random string (e.g. `openssl rand -base64 48`)
- `FRONTEND_URL` — filled in step 8, once you have the Netlify URL

Render sets `PORT` automatically — no action needed.

## 4. Get the backend's public URL
After the first successful deploy, copy the service URL, e.g. `https://queueflow-backend.onrender.com`.

## 5. Frontend on Netlify
1. Netlify → Add new site → Import from Git (or drag-and-drop `frontend/dist` after a local `npm run build` if not using Git).
2. `netlify.toml` at the repo root already sets base=`frontend`, build=`npm install && npm run build`, publish=`dist`, and the SPA redirect.

## 6. Configure `VITE_API_URL`
In Netlify → Site settings → Environment variables, set:
- `VITE_API_URL=https://queueflow-backend.onrender.com` (the URL from step 4, no trailing slash)

(`VITE_WS_URL` is optional — the WebSocket URL is derived from `VITE_API_URL` automatically.)

## 7. Deploy the frontend
Trigger a deploy (push to Git, or "Trigger deploy" in Netlify). Note the resulting site URL, e.g. `https://queueflow.netlify.app`.

## 8. Point the backend at the frontend
Back on Render, set `FRONTEND_URL=https://queueflow.netlify.app` on the backend service and redeploy (or let it restart) so CORS allows the real origin.

## 9. Test the WebSocket
Open the Netlify site's `/display` page in one tab and the attendant screen in another; call a ticket and confirm the display updates live. Check the browser console — the STOMP/SockJS connection should use `wss://` against the Render URL, with no CORS errors.

## 10. Smoke test
- `https://queueflow-backend.onrender.com/api/public/health` → `{"status":"UP"}`
- Register the first user via the frontend login screen (becomes `ADMIN` automatically — no dev data is seeded in `prod`)
- Create a queue and a counter, issue a ticket from `/totem`, call it from `/attendant`, confirm `/display` updates live
