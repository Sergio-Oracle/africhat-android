# AfriChat Android

**AfriChat** est un client [Matrix](https://matrix.org/) Android panafricain et souverain, propulsé par [matrix-rust-sdk](https://github.com/matrix-org/matrix-rust-sdk).

Conçu pour la souveraineté numérique africaine : chiffrement bout-en-bout, appels de groupe via LiveKit, notifications sans dépendance Google (UnifiedPush), 100 % logiciels libres.

> **Licence** : AGPL-3.0 — tout fork doit rester open source.

---

## Table des matières

- [Architecture](#architecture)
- [Configuration du homeserver](#configuration-du-homeserver)
- [Appels de groupe LiveKit](#appels-de-groupe-livekit)
  - [Déploiement du token server](#déploiement-du-token-server)
  - [Déploiement de LiveKit Server](#déploiement-de-livekit-server)
  - [Variables de configuration](#variables-de-configuration)
- [Notifications](#notifications)
  - [Firebase FCM](#firebase-fcm)
  - [UnifiedPush (sans Google)](#unifiedpush-sans-google)
- [Build instructions](#build-instructions)
- [Contribuer](#contribuer)
- [Crédits](#crédits)

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  AfriChat Android (ce dépôt)                        │
│  UI: Jetpack Compose + Appyx navigation             │
│  SDK: matrix-rust-sdk (.aar dans libraries/rustsdk) │
│  Appels: AfriChatGroupCallService → LiveKit SDK     │
└──────────────────┬──────────────────────────────────┘
                   │ Matrix CS API
┌──────────────────▼──────────────────────────────────┐
│  Synapse Homeserver (jn.rtn.sn)                     │
│  Gère: comptes, salles, messages, chiffrement E2EE  │
└──────────────────┬──────────────────────────────────┘
                   │ Événement io.africhat.group_call
┌──────────────────▼──────────────────────────────────┐
│  Token Server LiveKit                               │
│  Vérifie le token Matrix → retourne un JWT LiveKit  │
│  Source: /opt/livekit-token-server/server.js        │
└──────────────────┬──────────────────────────────────┘
                   │ JWT LiveKit
┌──────────────────▼──────────────────────────────────┐
│  LiveKit Server (wss://livekit.ec2lt.sn)            │
│  SFU WebRTC: gère les flux audio/vidéo              │
│  Simulcast 360p/720p/1080p, partage d'écran         │
└─────────────────────────────────────────────────────┘
```

---

## Configuration du homeserver

Le homeserver par défaut est `jn.rtn.sn`. Pour changer :

```kotlin
// appconfig/src/main/kotlin/io/element/android/appconfig/AuthenticationConfig.kt
const val MATRIX_ORG_URL = "https://votre-homeserver.com"
```

---

## Appels de groupe LiveKit

AfriChat utilise **LiveKit** (WebRTC SFU) pour les appels audio/vidéo de groupe. Le signaling passe par un événement Matrix custom `io.africhat.group_call` — **pas** d'Element Call / WebView requis.

### Flux d'un appel de groupe

```
1. Utilisateur clique "Appel groupe"
2. AfriChat génère un callId = group_<timestamp>
3. Requête GET au token server :
   GET https://<token-server>/api/connection-details?room=<callId>
   Authorization: Bearer <matrix_access_token>
   ← { "token": "eyJ...", "url": "wss://..." }

4. Envoie dans la salle Matrix :
   type: io.africhat.group_call
   content: { action: "invite", call_id, livekit_room, caller, with_video }

5. Connexion directe à LiveKit avec le JWT
6. Les autres membres reçoivent l'événement → sonnerie → rejoignent
```

### Déploiement du token server

Le token server est un serveur Node.js léger qui :
1. Reçoit le token Matrix de l'utilisateur
2. Le vérifie auprès de votre homeserver (`/_matrix/client/v3/account/whoami`)
3. Génère un JWT LiveKit signé avec votre API key/secret

**Code source du token server** : disponible dans `/opt/livekit-token-server/` sur votre serveur.

#### Installation rapide

```bash
# 1. Cloner et installer
git clone https://github.com/Sergio-Oracle/africhat-android
cd africhat-android
# Le token server est dans /opt/livekit-token-server/ sur votre serveur

# Ou créer depuis zéro :
mkdir livekit-token-server && cd livekit-token-server
npm init -y
npm install node-fetch
```

**Fichier `server.js`** (copier-coller depuis `/opt/livekit-token-server/server.js` sur votre serveur) :

```javascript
const LIVEKIT_API_KEY    = process.env.LIVEKIT_API_KEY    || 'your-api-key';
const LIVEKIT_API_SECRET = process.env.LIVEKIT_API_SECRET || 'your-api-secret';
const MATRIX_HOMESERVER  = process.env.MATRIX_HOMESERVER  || 'https://jn.rtn.sn';
const LIVEKIT_URL        = process.env.LIVEKIT_URL        || 'wss://livekit.example.com';
// ... voir le fichier complet dans /opt/livekit-token-server/server.js
```

**Fichier `.env`** (à créer, ne jamais committer) :

```env
LIVEKIT_API_KEY=votre-api-key
LIVEKIT_API_SECRET=votre-api-secret-tres-long
MATRIX_HOMESERVER=https://jn.rtn.sn
LIVEKIT_URL=wss://livekit.votre-domaine.com
PORT=3001
```

**Démarrage avec systemd** :

```ini
# /etc/systemd/system/livekit-token-server.service
[Unit]
Description=AfriChat LiveKit Token Server
After=network.target

[Service]
Type=simple
User=www-data
WorkingDirectory=/opt/livekit-token-server
EnvironmentFile=/opt/livekit-token-server/.env
ExecStart=/usr/bin/node server.js
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
systemctl enable livekit-token-server
systemctl start livekit-token-server
```

**Configuration Nginx (proxy HTTPS)** :

```nginx
location /api/connection-details {
    proxy_pass http://127.0.0.1:3001/api/connection-details;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    add_header Access-Control-Allow-Origin "*";
}
```

**Test rapide** :

```bash
# Obtenir un token Matrix d'abord
TOKEN=$(curl -s -X POST https://jn.rtn.sn/_matrix/client/v3/login \
  -H "Content-Type: application/json" \
  -d '{"type":"m.login.password","user":"@vous:jn.rtn.sn","password":"votremotdepasse"}' \
  | jq -r .access_token)

# Tester le token server
curl "https://votre-serveur/api/connection-details?room=test_room" \
  -H "Authorization: Bearer $TOKEN"
# Réponse attendue: { "token": "eyJ...", "url": "wss://..." }
```

---

### Déploiement de LiveKit Server

#### Option A — Docker (recommandé)

```bash
# Générer les clés API
docker run --rm livekit/livekit-server generate-keys
# → API Key: monapi
# → Secret: monsecret-tres-long-au-moins-32-chars

# Créer la config
cat > /etc/livekit/config.yaml << 'EOF'
port: 7880
rtc:
  udp_port: 50000-60000
  tcp_port: 7881
  use_external_ip: true
keys:
  votre-api-key: votre-api-secret
logging:
  level: info
EOF

# Lancer
docker run -d \
  --name livekit \
  --network host \
  -v /etc/livekit:/etc/livekit \
  livekit/livekit-server --config /etc/livekit/config.yaml
```

#### Option B — Binaire natif

```bash
# Télécharger
curl -L https://github.com/livekit/livekit/releases/latest/download/livekit_linux_amd64.tar.gz | tar xz
sudo mv livekit-server /usr/local/bin/

# Lancer
livekit-server --config /etc/livekit/config.yaml
```

#### Ports à ouvrir dans le firewall

```bash
ufw allow 7880/tcp   # HTTP API LiveKit
ufw allow 7881/tcp   # WebRTC TCP
ufw allow 50000:60000/udp  # WebRTC UDP (RTP)
```

---

### Variables de configuration

Dans le code Android, modifier `appconfig/src/main/kotlin/io/element/android/appconfig/ElementCallConfig.kt` :

```kotlin
object ElementCallConfig {
    const val LIVEKIT_URL = "wss://livekit.votre-domaine.com"
    const val LIVEKIT_API_KEY = "votre-api-key"
    const val LIVEKIT_TOKEN_SERVER_URL = "https://votre-serveur/api/connection-details"
    const val AFRICHAT_GROUP_CALL_EVENT_TYPE = "io.africhat.group_call"
}
```

---

## Notifications

### Firebase FCM

Firebase est activé par défaut pour les notifications push. Requis : un projet Firebase avec `google-services.json` dans `app/`.

### UnifiedPush (sans Google)

AfriChat supporte **UnifiedPush** pour les notifications sans serveurs Google. L'utilisateur installe une app distributeur UnifiedPush sur son appareil :

- [ntfy](https://ntfy.sh/) (recommandé, auto-hébergeable)
- [Gotify UP](https://github.com/gotify/android)
- [Unified Push distributor](https://unifiedpush.org/)

**Déployer votre propre serveur ntfy** :

```bash
docker run -d \
  --name ntfy \
  -p 8080:80 \
  -v /var/ntfy:/etc/ntfy \
  binwiederhier/ntfy serve
```

Les deux (Firebase + UnifiedPush) sont activés simultanément (`PUSH_CONFIG_INCLUDE_FIREBASE = true`, `PUSH_CONFIG_INCLUDE_UNIFIED_PUSH = true`). L'app utilise le premier disponible sur l'appareil.

---

## Build instructions

### Prérequis

- **Java 17+** (JDK)
- **Android Studio Ladybug** ou supérieur
- **Android SDK** avec API 35
- **NDK** (pour le SDK Rust compilé)

### Build debug (APK de test)

```bash
git clone https://github.com/Sergio-Oracle/africhat-android
cd africhat-android
./gradlew assembleDebug
# APK généré: app/build/outputs/apk/debug/app-debug.apk
```

### Build release

```bash
./gradlew assembleRelease
# Nécessite un keystore de signature
```

### Build via GitHub Actions

Chaque push sur `main` déclenche automatiquement la CI qui produit un APK debug téléchargeable dans les Artifacts de l'action.

---

## Contribuer

1. Fork le dépôt
2. Créer une branche : `git checkout -b feature/ma-fonctionnalite`
3. Committer : `git commit -m "feat: ma fonctionnalite"`
4. Push : `git push origin feature/ma-fonctionnalite`
5. Ouvrir une Pull Request

**Langues africaines bienvenues** — les traductions vont dans `libraries/ui-strings/src/main/res/values-*/`.

---

## Crédits

- SDK Matrix : [matrix-rust-sdk](https://github.com/matrix-org/matrix-rust-sdk) (Apache 2.0)
- Appels de groupe : [LiveKit](https://livekit.io/) (Apache 2.0)
- Licence : [AGPL-3.0](LICENSE)
