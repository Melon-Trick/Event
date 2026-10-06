# Publications de bibliothèques

Le workflow manuel Publish library vérifie puis publie une version x.y.z dans
GitHub Packages, dans le dépôt qui possède cette bibliothèque. La source locale
conserve sa version de développement. Les versions publiées ne se remplacent pas.
Une nouvelle publication nécessite une nouvelle version. Une publication multi-artifact
interrompue n'est pas atomique : ne l'adoptez dans le BOM du consommateur qu'une fois
le workflow entier réussi. Réexécuter une publication partielle peut rencontrer des
artifacts déjà existants ; corriger puis utiliser une nouvelle version.

Coordonnée d'alignement : dev.vriege:event-framework. Les consommateurs Maven et Gradle lisent les mêmes
POM. Les BOM fixent des versions compatibles ; ils ne téléchargent pas de bibliothèque
à eux seuls. Les dépendances réellement utilisées restent explicites.

Les packages privés restent rattachés à leur dépôt privé. Même les lectures de packages
publics GitHub Maven demandent une authentification. La publication CI utilise son
GITHUB_TOKEN avec packages:write. Les lectures entre dépôts privés utilisent un token
classic avec read:packages et accès aux dépôts concernés, ou un GITHUB_TOKEN auquel
l'accès aux packages a été explicitement accordé. Aucun token n'est stocké dans ce dépôt.

Pour les lecteurs Maven : utiliser le modèle .mvn/settings-packages.xml si présent,
avec le profil github-read et les variables MELONTRICK_PACKAGES_USER et
MELONTRICK_PACKAGES_TOKEN. Pour Gradle : déclarer le même endpoint Maven avec des
credentials issus du fichier utilisateur ~/.gradle/gradle.properties ou de l'environnement.
Ne pas passer un secret sur la ligne de commande, ni l'écrire dans gradle.properties
versionné. Désactiver le configuration cache pour une publication authentifiée.

La publication privée distante n'a pas été exécutée automatiquement. Le workflow est
un outil à déclencher après choix de version ; il ne publie pas à chaque push.

Référence : https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry
