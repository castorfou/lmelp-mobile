# Configuration de l'environnement de développement Android

Ce guide décrit les étapes pour configurer l'environnement de développement Android dans le devcontainer.

## Prérequis

Le devcontainer ne contient pas Java par défaut. Il faut l'installer manuellement (ou via le postCreateCommand).

## Installation de Java (JDK 17)

Android requiert Java 17. L'installer avec :

```bash
sudo apt-get update -qq
sudo apt-get install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
echo 'export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64' >> ~/.bashrc
```

Vérifier l'installation :

```bash
java -version
# Expected: openjdk version "17.x.x" ...
```

## Installation du SDK Android

```bash
# Créer le répertoire SDK
mkdir -p ~/android-sdk/cmdline-tools

# Télécharger les command-line tools
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip
unzip -q /tmp/cmdline-tools.zip -d ~/android-sdk/cmdline-tools/
mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest

# Configurer les variables d'environnement
export ANDROID_HOME=~/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Persister dans .bashrc
echo 'export ANDROID_HOME=~/android-sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc

# Accepter les licences et installer les composants nécessaires
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

## Configuration local.properties

Le fichier `local.properties` (ignoré par git) doit pointer vers le SDK Android :

```
sdk.dir=/home/vscode/android-sdk
```

Ce fichier est déjà configuré dans le repo.

## Build Android

```bash
# Build debug APK
./gradlew assembleDebug

# Lancer les tests unitaires
./gradlew test

# Build release APK
./gradlew assembleRelease
```

## Intégration dans postCreateCommand.sh

Pour automatiser cette configuration à la création du devcontainer, les étapes ci-dessus doivent être intégrées dans `.devcontainer/postCreateCommand.sh`.

!!! note "TODO"
    Ces étapes seront intégrées dans `postCreateCommand.sh` une fois validées manuellement.

## Lancer les tests unitaires

Les tests unitaires Android ne nécessitent pas d'émulateur :

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/home/vscode/android-sdk
./gradlew :app:testDebugUnitTest
```
