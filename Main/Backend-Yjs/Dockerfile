FROM node:22-alpine

WORKDIR /app

ENV NODE_ENV=production \
    PORT=3000

COPY package.json package-lock.json ./

RUN npm ci --omit=dev \
    && npm cache clean --force

COPY --chown=node:node CrdtServer.js collaboration-auth.js ./
COPY --chown=node:node --chmod=0444 certs/ap-northeast-2-bundle.pem /app/certs/rds-ca-bundle.pem

RUN chmod 0755 /app/certs

USER node

EXPOSE 3000

CMD ["node", "CrdtServer.js"]
