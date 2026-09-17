# Hybrid Remote (Android TV Wi-Fi + Infrarrojo)

Proyecto de Android Studio listo para abrir y compilar. Une control por
Wi-Fi (ADB, para TV Box y Android TV) con control por infrarrojo (para
TVs normales), en una sola app con diseño oscuro estilo Android TV.

## Cómo compilar SIN PC, solo con el teléfono (recomendado en tu caso)

Usamos Termux (que ya tienes) solo para subir el código a GitHub; quien
compila de verdad es GitHub, en sus servidores, gratis. El resultado es
un archivo `.apk` que descargas desde el navegador.

1. **Crea una cuenta en GitHub** (github.com) desde el navegador del
   teléfono, si no tienes una.
2. En github.com, crea un repositorio nuevo, vacío, por ejemplo
   `hybrid-remote`. NO marques "Add README".
3. En GitHub, ve a **Settings → Developer settings → Personal access
   tokens → Tokens (classic) → Generate new token**. Marca el permiso
   `repo` y genera el token. **Cópialo**, solo se muestra una vez.
4. Descarga `HybridRemote.zip` (el que te pasé) a tu teléfono, y en
   Termux:
   ```
   pkg install git unzip -y
   cd storage/downloads   # si no existe, antes corre: termux-setup-storage
   unzip HybridRemote.zip
   cd HybridRemote
   git init
   git add .
   git commit -m "Primera versión"
   git branch -M main
   git remote add origin https://TU_USUARIO:TU_TOKEN@github.com/TU_USUARIO/hybrid-remote.git
   git push -u origin main
   ```
   (Cambia `TU_USUARIO`, `TU_TOKEN` y el nombre del repo por los tuyos.)
5. Ve a tu repositorio en GitHub, pestaña **Actions**. Verás que
   empezó a compilar solo (gracias al archivo `.github/workflows/build.yml`
   que ya viene incluido). Tarda unos 3-5 minutos.
6. Cuando termine (✅ verde), entra a ese workflow y baja hasta
   **Artifacts** → descarga `HybridRemote-debug-apk`. Es un .zip que
   contiene el `app-debug.apk`.
7. Descomprímelo, abre el `.apk` desde tu explorador de archivos y
   dale instalar (activa "instalar apps de fuentes desconocidas" si
   te lo pide).

## Cómo abrir y compilar con PC (alternativa, si en algún momento tienes uno)

1. Instala **Android Studio** (gratis, en developer.android.com/studio).
2. Abre esta carpeta completa (`HybridRemote/`) como proyecto.
3. Deja que Gradle sincronice (necesita internet la primera vez, para
   descargar las librerías).
4. Conecta tu teléfono por USB (con "depuración USB" activada) o usa un
   emulador, y dale ▶ Run. Eso instala la app y la abre.
5. Cuando quieras el archivo `.apk` para compartir: menú **Build →
   Build App Bundle(s) / APK(s) → Build APK(s)**. El archivo queda en
   `app/build/outputs/apk/debug/app-debug.apk`.

## Cómo usar la app

**Modo Wi-Fi (Android TV / TV Box, como tu H96 Max):**
1. En el TV: Ajustes → Preferencias del dispositivo → Información →
   pulsa 7 veces sobre "Compilación" para activar Opciones de
   desarrollador.
2. Activa "Depuración por red" o "ADB por Wi-Fi" (el nombre varía
   según el fabricante).
3. Anota la IP del TV (Ajustes → Red).
4. En la app, escribe esa IP y pulsa "Conectar".
5. La **primera vez**, en la pantalla del TV aparecerá un aviso pidiendo
   autorizar la conexión — hay que aceptarlo ahí, una sola vez. Esto
   es una protección de seguridad de Android que ninguna app puede
   evitar (ni Unimote, ni Google Home, ni ninguna otra).

**Modo Infrarrojo (TVs normales, sin Android):**
- Solo funciona si el teléfono tiene un chip emisor de IR físico
  (Xiaomi, algunos Huawei/Samsung). La app detecta esto solo y avisa
  si no está disponible.
- Ahora mismo incluye un código de ejemplo (Samsung, encendido). Para
  que sirva con más marcas y botones, añade más códigos NEC a
  `IrController.kt` — puedes sacarlos de bases públicas como
  https://github.com/probonopd/irdb

## Limitaciones honestas (para que no haya sorpresas)

- El modo IR **no** se puede activar por software si el teléfono no
  tiene el chip; es una limitación de hardware, no de la app.
- El modo Wi-Fi requiere que el usuario acepte un aviso de
  autorización una vez en su TV — no hay forma de evitarlo sin
  vulnerar la seguridad de Android.
- El cliente ADB incluido (`AdbClient.kt`) es una implementación
  propia y funcional del protocolo, pero antes de publicarla te
  recomiendo probarla contra varios modelos de TV/TV Box para
  afinar posibles casos raros de autenticación.

## Próximos pasos sugeridos

- Guardar la clave RSA generada (`AdbCrypto`) en `SharedPreferences`
  para no tener que re-autorizar cada vez que se abre la app.
- Añadir un selector de marca de TV para el modo IR.
- Añadir búsqueda automática de dispositivos Android TV en la red
  local (NSD/mDNS) para no tener que escribir la IP a mano.
