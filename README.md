# Claude API Kielenkääntäjä

Tämä projekti on osa Vitecin tekoälytyöpajaa. Se demonstroi Claude API:n käyttöä luonnollisen kielen kääntämiseen Java-ympäristössä.

## Käyttö

1. Hanki Claude API-avain Anthropicilta
2. Suorita sovellus: `java -jar target/claude-translator-1.0-SNAPSHOT-jar-with-dependencies.jar`
3. Anna API-avain pyydettäessä tai aseta se ympäristömuuttujaan `CLAUDE_API_KEY`

## Ominaisuudet

- Tekstin kääntäminen eri kielten välillä
- Tiedostojen kääntäminen
- Toimialakohtaisen kontekstin huomioiminen

## Konfiguraatio

API-avaimet on tallennettava config.properties-tiedostoon tai annettava ympäristömuuttujina.
