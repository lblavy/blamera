# Métadonnées vérifiées — jakarta.inject-api 2.0.1

La première globale M1-04 rejetait cet artefact comme GPL+Classpath. Le plugin collecte
par défaut l'union des licences des POM parents ; ce résultat ne correspond pas à la
licence explicite du module, de son manifeste JAR et de ses fichiers LICENSE/NOTICE :
**Apache-2.0** dans les quatre sources vérifiées.

- [POM officiel exact](https://repo.maven.apache.org/maven2/jakarta/inject/jakarta.inject-api/2.0.1/jakarta.inject-api-2.0.1.pom).
- [JAR officiel exact](https://repo.maven.apache.org/maven2/jakarta/inject/jakarta.inject-api/2.0.1/jakarta.inject-api-2.0.1.jar).
- [Licence du tag éditeur2.0.1](https://github.com/jakartaee/inject/blob/2.0.1/LICENSE.txt).
- Copies exactes du JAR : [LICENSE.txt](LICENSE.txt), [NOTICE.md](NOTICE.md).
- [Provenance et SHA-256 du POM, JAR et textes](provenance.json).

`config/license-normalization.json` normalise **uniquement** les coordonnées exactes
`jakarta.inject:jakarta.inject-api:2.0.1`. Aucune règle par nom de licence, URL générique
ou préfixe de groupe n'est ajoutée. Le second normaliseur désactive ses règles implicites ;
le normaliseur SPDX initial et l'union des parents restent inchangés pour les autres modules.
La liste de licences autorisées n'est pas élargie ; une autre version ou un autre module
GPL reste soumis au gate existant. Aucun helper de gate ou parser n'est modifié.

Validation réelle : génération du rapport puis vérification de toutes les entrées avec
la politique existante, sortie0. Regex vérifiée sur la version exacte et des contre-exemples
(autre module, version2.0.2, faux séparateur de groupe), sans correspondance élargie.
Les textes et hashes bornent cette vérification à l'artefact examiné ; réexaminer les
métadonnées et les fichiers si le module, sa version ou son contenu change.
[API de normalisation officielle](https://github.com/jk1/Gradle-License-Report/blob/master/src/main/groovy/com/github/jk1/license/filter/LicenseBundleNormalizer.groovy).
