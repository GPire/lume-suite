package org.lumesuite.launcher;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Italian for the screens, in one place: every label goes through {@link HTextView}, which asks this class
 * for a translation. Only when the device language is Italian; any other language keeps the English text.
 * Strings that are not in the table (titles, headlines, anything dynamic) pass through untouched.
 */
final class It {
    /** Column of the device language in each row: it, es, de, fr, pt. English (and anything else) stays as written. */
    private static final int LANG = indexOf(Locale.getDefault().getLanguage());
    private static final boolean ON = LANG >= 0;
    private static final Map<String, String[]> M = new HashMap<>();

    private static int indexOf(String l) {
        switch (l) {
            case "it": return 0;
            case "es": return 1;
            case "de": return 2;
            case "fr": return 3;
            case "pt": return 4;
            default: return -1;
        }
    }

    private It() {
    }

    /** Italian only (older entries). */
    private static void p(String en, String it) {
        String[] r = M.get(en);
        if (r == null) {
            r = new String[5];
            M.put(en, r);
        }
        r[0] = it;
    }

    /** All five languages: it, es, de, fr, pt. */
    private static void q(String en, String it, String es, String de, String fr, String pt) {
        M.put(en, new String[]{it, es, de, fr, pt});
    }

    static {
        p("Action & Adventure", "Azione e avventura"); p("Action", "Azione"); p("Add User", "Aggiungi utente");
        p("Add-ons", "Componenti aggiuntivi"); p("Aerial Views", "Vedute aeree");
        p("After 1 minute", "Dopo 1 minuto"); p("After 10 seconds", "Dopo 10 secondi");
        p("After 20 seconds  (default)", "Dopo 20 secondi  (predefinito)"); p("After 30 seconds", "Dopo 30 secondi");
        p("All", "Tutti"); p("Always", "Sempre"); p("Animation", "Animazione"); p("App Info", "Info app");
        p("App info", "Info app"); p("Apps & Settings", "App e impostazioni"); p("Apps", "App");
        p("At boot", "All'avvio"); p("Auto-advance", "Avanzamento automatico"); p("Automatic", "Automatico");
        p("Back", "Indietro"); p("Background", "Sfondo"); p("Cancel", "Annulla"); p("Carousel", "Carosello");
        p("Change Colour", "Cambia colore"); p("Change Emoji", "Cambia emoji"); p("Choose City", "Scegli la città");
        p("Choose a Look", "Scegli un aspetto"); p("Clear history", "Cancella cronologia"); p("Clear", "Cancella");
        p("Cloudy", "Nuvoloso"); p("Comedy", "Commedia"); p("Connected", "Connesso"); p("Content", "Contenuti");
        p("Continue Watching & Your Apps", "Continua a guardare e le tue app");
        p("Continue Watching", "Continua a guardare"); p("Continue watching", "Continua a guardare");
        p("Continue", "Continua"); p("Control Center", "Centro di controllo");
        p("Create Your Profile", "Crea il tuo profilo"); p("Current profile", "Profilo attuale");
        p("Date & Time", "Data e ora"); p("Delete", "Elimina"); p("Display", "Schermo");
        p("Do Not Disturb", "Non disturbare"); p("Documentary", "Documentario"); p("Done", "Fatto");
        p("Drama", "Drammatico"); p("Drizzle", "Pioggerella"); p("English", "Inglese"); p("Family", "Famiglia");
        p("Finish Setting Up", "Completa la configurazione"); p("Foggy", "Nebbia"); p("Free", "Gratis");
        p("Full  (default)", "Pieno  (predefinito)"); p("Full", "Pieno"); p("Get Started", "Inizia");
        p("Go to Home Screen", "Vai alla Home"); p("Hide", "Nascondi"); p("History", "Cronologia");
        p("Hold OK to remove", "Tieni premuto OK per rimuovere"); p("Hold OK", "Tieni premuto OK");
        p("Home Screen  (default)", "Schermata Home  (predefinito)"); p("Home Screen", "Schermata Home");
        p("Home button opens Lume", "Il tasto Home apre Lume"); p("The Home button opens Lume", "Il tasto Home apre Lume");
        p("How to Watch", "Come guardare"); p("HOW TO WATCH", "COME GUARDARE"); p("Humidity ", "Umidità ");
        p("Inputs", "Ingressi"); p("Input", "Ingresso"); p("Just now", "Adesso");
        p("Keep Screen Awake", "Tieni lo schermo acceso"); p("Languages", "Lingue"); p("Location", "Posizione");
        p("Memory", "Memoria"); p("Midnight", "Mezzanotte"); p("More Apps", "Altre app");
        p("Mostly Clear", "Prevalentemente sereno"); p("Mostly Sunny", "Prevalentemente soleggiato");
        p("Motion & Sound", "Movimento e suono"); p("Motion", "Movimento"); p("Move", "Sposta"); p("Movie", "Film");
        p("Movies & Series", "Film e serie"); p("Movies & TV", "Film e TV"); p("Movies", "Film"); p("Music", "Musica");
        p("Name", "Nome"); p("Navigation sounds", "Suoni di navigazione"); p("New Profile", "Nuovo profilo");
        p("News", "Notizie"); p("No internet", "Nessuna connessione"); p("No results", "Nessun risultato");
        p("None", "Nessuno"); p("Not Connected", "Non connesso"); p("Not connected", "Non connesso");
        p("Not installed", "Non installata"); p("Off", "Spento"); p("Offline", "Offline"); p("Online", "Online");
        p("Open", "Apri"); p("Open Accessibility", "Apri Accessibilità"); p("Partly Cloudy", "Parzialmente nuvoloso");
        p("Permissions", "Permessi"); p("Popular", "Popolari"); p("Power Off", "Spegni");
        p("Presentation", "Presentazione"); p("Profiles", "Profili"); p("Rain", "Pioggia");
        p("Heavy Rain", "Pioggia forte"); p("Recent Searches", "Ricerche recenti");
        p("Recent searches", "Ricerche recenti"); p("Recently watched", "Guardati di recente");
        p("Recommendations", "Raccomandazioni"); p("Reduced", "Ridotto"); p("Refresh Carousel", "Aggiorna carosello");
        p("Rename Profile", "Rinomina profilo"); p("Rename", "Rinomina"); p("Reset Layout", "Ripristina ordine");
        p("Resume", "Riprendi"); p("Run setup again", "Ripeti la configurazione"); p("Save", "Salva");
        p("Search", "Cerca"); p("Search apps, movies and shows", "Cerca app, film e serie"); p("Series", "Serie");
        p("TV Series", "Serie TV"); p("Settings", "Impostazioni"); p("Setup", "Configurazione");
        p("Show on Home Screen", "Mostra nella Home"); p("Silver", "Argento"); p("Sky", "Cielo");
        p("Sleep Timer", "Riposo"); p("Sleet", "Nevischio"); p("Snow", "Neve"); p("Sounds", "Suoni");
        p("Sport", "Sport"); p("Storage", "Spazio"); p("Sunny", "Soleggiato"); p("Sunset", "Tramonto");
        p("Switch Profile", "Cambia profilo"); p("TV Speakers", "Altoparlanti TV"); p("Thunderstorms", "Temporali");
        p("Today", "Oggi"); p("Top Shelf", "Vetrina"); p("Trending", "Di tendenza"); p("Uninstall", "Disinstalla");
        p("Unknown", "Sconosciuto"); p("Version ", "Versione "); p("Weather", "Meteo");
        p("Weather Forecast", "Previsioni meteo"); p("Welcome", "Benvenuto"); p("Who's Watching?", "Chi sta guardando?");
        p("Who's watching?", "Chi sta guardando?"); p("Yes", "Sì"); p("Yesterday", "Ieri");
        p("Your Home Screen", "La tua Home"); p("Your name", "Il tuo nome"); p("Not Now", "Non ora");
        p("Make Lume the Home Screen", "Rendi Lume la Home"); p("Make Home Screen", "Rendi Lume la Home");
        p("Lume Settings", "Impostazioni di Lume"); p("Wi-Fi Name in Control Center", "Nome Wi-Fi nel Centro di controllo");
        p("Wi-Fi name in Control Center", "Nome Wi-Fi nel Centro di controllo");
        p("Show weather", "Mostra il meteo"); p("Weather location", "Posizione del meteo");
        p("Use My Location", "Usa la mia posizione"); p("My Location", "La mia posizione");
        p("Temperature", "Temperatura"); p("Celsius", "Celsius"); p("Fahrenheit", "Fahrenheit");
        p("Screensaver", "Salvaschermo"); p("Screen Saver", "Salvaschermo"); p("Sound", "Suono");
        p("Streaming services", "Servizi di streaming"); p("Suggestions", "Suggerimenti");
        p("Play Next", "Prossimo"); p("Up Next", "A seguire"); p("Now", "Adesso");
        p("Refreshing", "Aggiornamento"); p("Refresh now", "Aggiorna ora"); p("No apps to close", "Nessuna app da chiudere");
        p("Switch", "Cambia"); p("Welcome", "Benvenuto"); p("Get", "Ottieni"); p("Buy", "Acquista"); p("Rent", "Noleggia");
        p("Not set", "Non impostato"); p("Not needed", "Non necessario"); p("When", "Quando");
        p("Update every", "Aggiorna ogni"); p("Update weather every", "Aggiorna il meteo ogni");
        p("Every 6 s", "Ogni 6 s"); p("Every 8 s", "Ogni 8 s"); p("Every 12 s", "Ogni 12 s");

        // ---- core labels in every supported language (it, es, de, fr, pt)
        q("Power Off", "Spegni", "Apagar", "Ausschalten", "Éteindre", "Desligar");
        q("Recent Apps", "App recenti", "Apps recientes", "Zuletzt benutzt", "Apps récentes", "Apps recentes");
        q("Close All", "Chiudi tutte", "Cerrar todas", "Alle schließen", "Tout fermer", "Fechar todas");
        q("No recent apps yet. Open an app from Home and it will appear here.", "Ancora nessuna app recente. Aprine una dalla Home e la ritroverai qui.", "Aún no hay apps recientes. Abre una desde el Inicio y aparecerá aquí.", "Noch keine zuletzt benutzten Apps. Öffne eine auf dem Home und sie erscheint hier.", "Aucune app récente. Ouvre-en une depuis l'accueil et elle apparaîtra ici.", "Ainda sem apps recentes. Abre uma no Início e aparece aqui.");
        q("No recent apps yet", "Ancora nessuna app recente", "Aún no hay apps recientes", "Noch keine zuletzt benutzten Apps", "Aucune app récente", "Ainda sem apps recentes");
        q("Press ▲ on an app to close it", "Premi ▲ su un'app per chiuderla", "Pulsa ▲ en una app para cerrarla", "▲ auf einer App schließt sie", "Appuie sur ▲ pour fermer une app", "Prime ▲ numa app para a fechar");
        q("RAM ", "RAM ", "RAM ", "RAM ", "RAM ", "RAM ");
        q("Ambient Mode", "Ambiente", "Ambiente", "Ambiente", "Ambiance", "Ambiente");
        q("Screensaver", "Galleria", "Galería", "Galerie", "Galerie", "Galeria");
        q("Screen Off", "Schermo spento", "Pantalla apagada", "Bildschirm aus", "Écran éteint", "Ecrã desligado");
        q("%d%% used", "%d%% in uso", "%d%% en uso", "%d%% belegt", "%d%% utilisé", "%d%% em uso");
        q("Closed %d apps", "Chiuse %d app", "%d apps cerradas", "%d Apps beendet", "%d apps fermées", "%d apps fechadas");
        q("freed %d MB", "liberati %d MB", "%d MB liberados", "%d MB frei", "%d Mo libérés", "%d MB libertados");
        q("Allow\u2026", "Consenti\u2026", "Permitir\u2026", "Erlauben\u2026", "Autoriser\u2026", "Permitir\u2026");
        q("Set\u2026", "Imposta\u2026", "Establecer\u2026", "Festlegen\u2026", "D\u00E9finir\u2026", "Definir\u2026");
        q("Start on boot", "Avvia all'accensione", "Iniciar al encender", "Beim Einschalten starten", "D\u00E9marrer \u00E0 l'allumage", "Iniciar ao ligar");
        q("Turn on first", "Prima attivala", "Act\u00EDvalo primero", "Zuerst einschalten", "Activez-le d'abord", "Ative primeiro");
        q("This device has no TV inputs", "Questo dispositivo non ha ingressi TV", "Este dispositivo no tiene entradas de TV", "Dieses Ger\u00E4t hat keine TV-Eing\u00E4nge", "Cet appareil n'a pas d'entr\u00E9es TV", "Este dispositivo n\u00E3o tem entradas de TV");
        q("Home look", "Aspetto della Home", "Aspecto del Inicio", "Aussehen des Home", "Apparence de l'accueil", "Aspeto do In\u00EDcio");
        q("Scene", "Scena", "Escena", "Szene", "Sc\u00E8ne", "Cena");
        q("Classic", "Classico", "Cl\u00E1sico", "Klassisch", "Classique", "Cl\u00E1ssico");
        q("Good morning", "Buongiorno", "Buenos d\u00EDas", "Guten Morgen", "Bonjour", "Bom dia");
        q("Good afternoon", "Buon pomeriggio", "Buenas tardes", "Guten Tag", "Bon apr\u00E8s-midi", "Boa tarde");
        q("Good evening", "Buonasera", "Buenas noches", "Guten Abend", "Bonsoir", "Boa noite");
        q("Good night", "Buonanotte", "Buenas noches", "Gute Nacht", "Bonne nuit", "Boa noite");
        q("Phone control", "Controllo da telefono", "Control desde el móvil", "Steuerung per Handy", "Contrôle depuis le téléphone", "Controlo pelo telemóvel");
        q("Show the code to pair a phone", "Mostra il codice per collegare un telefono", "Mostrar el código para vincular un móvil", "Code zum Koppeln anzeigen", "Afficher le code pour associer un téléphone", "Mostrar o código para ligar um telemóvel");
        q("Control Lume from your phone", "Controlla Lume dal telefono", "Controla Lume desde tu móvil", "Lume per Handy steuern", "Contrôle Lume depuis ton téléphone", "Controla o Lume pelo telemóvel");
        q("Scan the code, then type the number you see here. Your phone and the TV must be on the same Wi-Fi.", "Inquadra il codice e scrivi il numero che vedi qui. Telefono e TV devono essere sullo stesso Wi-Fi.", "Escanea el código y escribe el número que ves aquí. El móvil y la TV deben estar en la misma Wi-Fi.", "Code scannen und die Zahl hier eingeben. Handy und TV müssen im selben WLAN sein.", "Scanne le code et saisis le nombre affiché ici. Téléphone et TV doivent être sur le même Wi-Fi.", "Lê o código e escreve o número que vês aqui. Telemóvel e TV têm de estar na mesma Wi-Fi.");
        q("Your code", "Il tuo codice", "Tu código", "Dein Code", "Ton code", "O teu código");
        q("It changes each time Lume restarts. A phone that has paired once stays paired.", "Cambia a ogni riavvio di Lume. Un telefono già collegato resta collegato.", "Cambia cada vez que Lume se reinicia. Un móvil ya vinculado sigue vinculado.", "Er ändert sich bei jedem Neustart von Lume. Ein gekoppeltes Handy bleibt gekoppelt.", "Il change à chaque redémarrage de Lume. Un téléphone déjà associé le reste.", "Muda sempre que o Lume reinicia. Um telemóvel já ligado continua ligado.");
        q("Amazon Home", "Home Amazon", "Inicio de Amazon", "Amazon Home", "Accueil Amazon", "Início Amazon");
        q("Appearance", "Aspetto", "Apariencia", "Erscheinungsbild", "Apparence", "Aparência");
        q("Liquid Glass", "Vetro liquido", "Cristal líquido", "Liquid Glass", "Verre liquide", "Vidro líquido");
        q("High contrast", "Alto contrasto", "Alto contraste", "Hoher Kontrast", "Contraste élevé", "Alto contraste");
        q("Memory", "Memoria", "Memoria", "Speicher", "Mémoire", "Memória");
        q("Wi-Fi", "Wi-Fi", "Wi-Fi", "WLAN", "Wi-Fi", "Wi-Fi");
        q("Connected", "Connesso", "Conectado", "Verbunden", "Connecté", "Conectado");
        q("Not connected", "Non connesso", "Sin conexión", "Nicht verbunden", "Non connecté", "Desconectado");
        q("Do Not Disturb", "Non disturbare", "No molestar", "Nicht stören", "Ne pas déranger", "Não perturbe");
        q("Audio", "Audio", "Audio", "Audio", "Audio", "Áudio");
        q("TV Speakers", "Altoparlanti TV", "Altavoces de la TV", "TV-Lautsprecher", "Haut-parleurs TV", "Alto-falantes da TV");
        q("Sleep Timer", "Riposo", "Temporizador", "Schlaftimer", "Minuterie", "Temporizador");
        q("Motion", "Movimento", "Movimiento", "Bewegung", "Mouvement", "Movimento");
        q("Sounds", "Suoni", "Sonidos", "Geräusche", "Sons", "Sons");
        q("On", "Acceso", "Activado", "An", "Activé", "Ativado");
        q("Off", "Spento", "Desactivado", "Aus", "Désactivé", "Desativado");
        q("Reduced", "Ridotto", "Reducido", "Reduziert", "Réduit", "Reduzido");
        q("Full", "Pieno", "Completo", "Voll", "Complet", "Completo");
        q("Open", "Apri", "Abrir", "Öffnen", "Ouvrir", "Abrir");
        q("Move", "Sposta", "Mover", "Verschieben", "Déplacer", "Mover");
        q("Hide", "Nascondi", "Ocultar", "Ausblenden", "Masquer", "Ocultar");
        q("App Info", "Info app", "Info de la app", "App-Info", "Infos sur l'app", "Info do app");
        q("Uninstall", "Disinstalla", "Desinstalar", "Deinstallieren", "Désinstaller", "Desinstalar");
        q("Reset Layout", "Ripristina ordine", "Restablecer orden", "Anordnung zurücksetzen", "Réinitialiser l'ordre", "Redefinir ordem");
        q("Show on Home Screen", "Mostra nella Home", "Mostrar en Inicio", "Auf dem Startbildschirm zeigen", "Afficher sur l'accueil", "Mostrar no Início");
        q("Settings", "Impostazioni", "Ajustes", "Einstellungen", "Réglages", "Configurações");
        q("Search", "Cerca", "Buscar", "Suchen", "Rechercher", "Pesquisar");
        q("Control Center", "Centro di controllo", "Centro de control", "Kontrollzentrum", "Centre de contrôle", "Central de controle");
        q("Continue", "Continua", "Continuar", "Weiter", "Continuer", "Continuar");
        q("Back", "Indietro", "Atrás", "Zurück", "Retour", "Voltar");
        q("Done", "Fatto", "Hecho", "Fertig", "Terminé", "Concluído");
        q("Get Started", "Inizia", "Empezar", "Los geht's", "Commencer", "Começar");
        q("Let\u2019s set up Lume. It takes about a minute.", "Prepariamo Lume. Ci vuole circa un minuto.", "Vamos a preparar Lume. Lleva un minuto.", "Richten wir Lume ein. Es dauert etwa eine Minute.", "Configurons Lume. Cela prend environ une minute.", "Vamos configurar o Lume. Demora cerca de um minuto.");
        q("Pick a name and an avatar. Each profile keeps its own apps, background and Home Screen; up to three people can have one.", "Scegli un nome e un avatar. Ogni profilo ha le sue app, il suo sfondo e la sua Home; fino a tre persone.", "Elige un nombre y un avatar. Cada perfil guarda sus apps, fondo e inicio; hasta tres personas.", "Wähle Namen und Avatar. Jedes Profil hat eigene Apps, Hintergrund und Startseite; bis zu drei Personen.", "Choisissez un nom et un avatar. Chaque profil garde ses apps, son fond et son accueil ; jusqu\u2019à trois personnes.", "Escolha um nome e um avatar. Cada perfil guarda as suas apps, fundo e ecrã inicial; até três pessoas.");
        q("Avatar", "Avatar", "Avatar", "Avatar", "Avatar", "Avatar");
        q("The background behind your apps. Move through the list to preview, press OK to pick.", "Lo sfondo dietro le tue app. Scorri la lista per vedere l\u2019anteprima, premi OK per sceglierlo.", "El fondo detrás de tus apps. Recorre la lista para verlo, pulsa OK para elegir.", "Der Hintergrund hinter deinen Apps. Blättere durch die Liste zur Vorschau, mit OK wählen.", "Le fond derrière vos apps. Parcourez la liste pour l\u2019aperçu, OK pour choisir.", "O fundo atrás das suas apps. Percorra a lista para pré-visualizar, OK para escolher.");
        q("Press a row to allow it. Only the first one matters for most people; the rest can wait.", "Premi una riga per consentirla. Per la maggior parte delle persone conta solo la prima; il resto può aspettare.", "Pulsa una fila para permitirla. Para casi todos solo importa la primera; el resto puede esperar.", "Drücke eine Zeile, um sie zu erlauben. Meist zählt nur die erste; der Rest kann warten.", "Appuyez sur une ligne pour l\u2019autoriser. Seule la première compte pour la plupart ; le reste peut attendre.", "Prima uma linha para a permitir. Para a maioria só a primeira importa; o resto pode esperar.");
        q("So the Home button opens Lume", "Così il tasto Home apre Lume", "Así el botón Inicio abre Lume", "So öffnet die Home-Taste Lume", "Ainsi la touche Accueil ouvre Lume", "Assim o botão Início abre o Lume");
        q("\u2713 Done", "\u2713 Fatto", "\u2713 Hecho", "\u2713 Fertig", "\u2713 Terminé", "\u2713 Feito");
        q("Set Up  \u203A", "Imposta  \u203A", "Configurar  \u203A", "Einrichten  \u203A", "Configurer  \u203A", "Configurar  \u203A");
        q("\u2713 Allowed", "\u2713 Consentito", "\u2713 Permitido", "\u2713 Erlaubt", "\u2713 Autorisé", "\u2713 Permitido");
        q("Allow  \u203A", "Consenti  \u203A", "Permitir  \u203A", "Erlauben  \u203A", "Autoriser  \u203A", "Permitir  \u203A");
        q("Read the lists your apps publish for Home", "Leggi le liste che le tue app pubblicano per la Home", "Leer las listas que tus apps publican para el inicio", "Listen lesen, die deine Apps für die Startseite veröffentlichen", "Lire les listes que vos apps publient pour l\u2019accueil", "Ler as listas que as suas apps publicam para o início");
        q("Power Off & Sleep Timer", "Spegnimento e timer di sospensione", "Apagado y temporizador", "Ausschalten & Schlaftimer", "Extinction et minuterie de veille", "Desligar e temporizador");
        q("Let Lume turn the screen off", "Lascia che Lume spenga lo schermo", "Deja que Lume apague la pantalla", "Lume darf den Bildschirm ausschalten", "Laisser Lume éteindre l\u2019écran", "Deixar o Lume desligar o ecrã");
        q("Android counts this as location access", "Android lo considera accesso alla posizione", "Android lo cuenta como acceso a la ubicación", "Android zählt das als Standortzugriff", "Android le compte comme un accès à la position", "O Android conta isto como acesso à localização");
        q("You\u2019re All Set", "Tutto pronto", "Todo listo", "Alles bereit", "Tout est prêt", "Tudo pronto");
        q("Change any of this later in Settings, from the gear in Control Center.", "Cambia tutto questo più tardi in Impostazioni, dall\u2019ingranaggio nel Centro di controllo.", "Cambia todo esto luego en Ajustes, desde el engranaje del Centro de control.", "Ändere das später in den Einstellungen über das Zahnrad im Kontrollzentrum.", "Modifiez tout cela plus tard dans Réglages, via l\u2019engrenage du Centre de contrôle.", "Altere tudo mais tarde em Definições, na roda dentada do Centro de controlo.");
        q("The background behind your apps. Move over a look to preview it, press OK to keep it.", "Lo sfondo dietro le tue app. Passa su un aspetto per vederlo, premi OK per tenerlo.", "El fondo detrás de tus apps. Pasa sobre un aspecto para verlo, pulsa OK para quedártelo.", "Der Hintergrund hinter deinen Apps. Gehe auf einen Look zur Vorschau, mit OK behalten.", "Le fond derrière vos apps. Survolez un style pour l\u2019aperçu, OK pour le garder.", "O fundo atrás das suas apps. Passe por um aspeto para o ver, OK para o manter.");
        q("Ocean", "Oceano", "Océano", "Ozean", "Océan", "Oceano");
        q("Graphite", "Grafite", "Grafito", "Graphit", "Graphite", "Grafite");
        q("Looking for the receiver…", "Cerco il ricevitore…", "Buscando el receptor…", "Suche den Empfänger…", "Recherche du récepteur…", "A procurar o recetor…");
        q("Make Lume your home screen?", "Vuoi che Lume sia la tua schermata Home?", "¿Quieres que Lume sea tu pantalla de inicio?", "Lume als Startseite verwenden?", "Faire de Lume votre écran d’accueil ?", "Quer que o Lume seja o seu ecrã inicial?");
        q("The Home button on the remote will open Lume.", "Il tasto Home del telecomando aprirà Lume.", "El botón Inicio del mando abrirá Lume.", "Die Home-Taste der Fernbedienung öffnet Lume.", "La touche Accueil de la télécommande ouvrira Lume.", "O botão Início do comando abrirá o Lume.");
        q("Back to return   ·   Hold OK for options", "Indietro per tornare   ·   Tieni premuto OK per le opzioni", "Atrás para volver   ·   Mantén OK para opciones", "Zurück zum Verlassen   ·   OK halten für Optionen", "Retour pour revenir   ·   Maintenez OK pour les options", "Voltar para regressar   ·   Mantenha OK para opções");
        q("Shows and movies you play will appear here.", "Qui compariranno i programmi e i film che guardi.", "Aquí aparecerán las series y películas que veas.", "Hier erscheinen Sendungen und Filme, die du ansiehst.", "Les séries et films que vous regardez apparaîtront ici.", "Aqui aparecerão as séries e filmes que vir.");
        q("Continue Watching needs Android TV 8 or later.", "Continua a guardare richiede Android TV 8 o successivo.", "Seguir viendo requiere Android TV 8 o posterior.", "Weiterschauen benötigt Android TV 8 oder neuer.", "Reprendre la lecture nécessite Android TV 8 ou plus récent.", "Continuar a ver requer Android TV 8 ou posterior.");
        q("To see what you watch here, allow it in Settings → Permissions.", "Per vedere qui cosa guardi, consentilo in Impostazioni → Permessi.", "Para ver aquí lo que ves, permítelo en Ajustes → Permisos.", "Um hier zu sehen, was du schaust, erlaube es unter Einstellungen → Berechtigungen.", "Pour voir ici ce que vous regardez, autorisez-le dans Réglages → Autorisations.", "Para ver aqui o que vê, permita em Definições → Permissões.");
        q("Remove from Continue Watching", "Rimuovi da Continua a guardare", "Quitar de Seguir viendo", "Aus Weiterschauen entfernen", "Retirer de Reprendre la lecture", "Remover de Continuar a ver");
        q("This TV has no inputs", "Questa TV non ha ingressi", "Esta TV no tiene entradas", "Dieser Fernseher hat keine Eingänge", "Cette TV n’a pas d’entrées", "Esta TV não tem entradas");
        q("Who’s Watching?", "Chi guarda?", "¿Quién está viendo?", "Wer schaut?", "Qui regarde ?", "Quem está a ver?");
        q("Its layout and Continue Watching list will be removed.", "Verranno rimossi la disposizione e l’elenco Continua a guardare.", "Se quitarán su diseño y su lista de Seguir viendo.", "Sein Layout und die Weiterschauen-Liste werden entfernt.", "Sa disposition et sa liste Reprendre la lecture seront supprimées.", "O seu layout e a lista Continuar a ver serão removidos.");
        q("Not available on this TV", "Non disponibile su questa TV", "No disponible en esta TV", "Auf diesem Fernseher nicht verfügbar", "Indisponible sur cette TV", "Indisponível nesta TV");
        q("Use arrows to move · OK when done", "Usa le frecce per spostare · OK quando hai finito", "Usa las flechas para mover · OK al terminar", "Mit Pfeilen bewegen · OK, wenn fertig", "Flèches pour déplacer · OK une fois terminé", "Use as setas para mover · OK quando terminar");
        q("Don't ask again", "Non chiedere più", "No volver a preguntar", "Nicht mehr fragen", "Ne plus demander", "Não perguntar de novo");
        q("Turn on Power Off", "Attiva lo spegnimento", "Activar el apagado", "Ausschalten aktivieren", "Activer l’extinction", "Ativar o desligar");
        q("Home screen", "Schermata Home", "Pantalla de inicio", "Startseite", "Écran d’accueil", "Ecrã inicial");
        q("Force stop", "Interrompi", "Forzar detención", "Beenden erzwingen", "Forcer l’arrêt", "Forçar paragem");
        q("Force Stop", "Interrompi", "Forzar detención", "Beenden erzwingen", "Forcer l’arrêt", "Forçar paragem");
        q("Say a movie, show or app", "Di’ un film, una serie o un’app", "Di una película, serie o app", "Sag einen Film, eine Serie oder App", "Dites un film, une série ou une app", "Diga um filme, série ou app");
        q("Voice input isn't available on this TV", "L’input vocale non è disponibile su questa TV", "La entrada de voz no está disponible en esta TV", "Spracheingabe ist auf diesem Fernseher nicht verfügbar", "La saisie vocale n’est pas disponible sur cette TV", "A entrada de voz não está disponível nesta TV");
        q("Voice needs the internet", "La voce richiede internet", "La voz necesita internet", "Sprache braucht Internet", "La voix nécessite internet", "A voz precisa de internet");
        q("Didn’t catch that — press the mic to try again", "Non ho capito — premi il microfono per riprovare", "No lo he entendido — pulsa el micrófono para repetir", "Nicht verstanden — Mikrofon drücken zum Wiederholen", "Pas compris — appuyez sur le micro pour réessayer", "Não percebi — prima o microfone para tentar de novo");
        q("Listening…", "Ascolto…", "Escuchando…", "Ich höre zu…", "J’écoute…", "A ouvir…");
        q("Can’t reach the internet", "Internet non raggiungibile", "No hay conexión a internet", "Kein Internetzugang", "Internet inaccessible", "Sem acesso à internet");
        q("Searching…", "Cerco…", "Buscando…", "Suche…", "Recherche…", "A pesquisar…");
        q("Keep typing to search movies and shows", "Continua a scrivere per cercare film e serie", "Sigue escribiendo para buscar películas y series", "Weitertippen, um Filme und Serien zu suchen", "Continuez à saisir pour chercher films et séries", "Continue a escrever para pesquisar filmes e séries");
        q("Type to search your apps, settings, movies and shows", "Scrivi per cercare app, impostazioni, film e serie", "Escribe para buscar apps, ajustes, películas y series", "Tippe, um Apps, Einstellungen, Filme und Serien zu suchen", "Saisissez pour chercher apps, réglages, films et séries", "Escreva para pesquisar apps, definições, filmes e séries");
        q("No movies or shows found", "Nessun film o serie trovato", "No se encontraron películas ni series", "Keine Filme oder Serien gefunden", "Aucun film ni série trouvé", "Nenhum filme ou série encontrado");
        q("All allowed", "Tutto consentito", "Todo permitido", "Alles erlaubt", "Tout autorisé", "Tudo permitido");
        q("Allowed", "Consentito", "Permitido", "Erlaubt", "Autorisé", "Permitido");
        q("Not on this TV", "Non su questa TV", "No en esta TV", "Nicht auf diesem Fernseher", "Pas sur cette TV", "Não nesta TV");
        q("From Your Apps", "Dalle tue app", "De tus apps", "Aus deinen Apps", "De vos apps", "Das suas apps");
        q("Continue Watching cleared", "Continua a guardare svuotato", "Seguir viendo vaciado", "Weiterschauen geleert", "Reprendre la lecture vidé", "Continuar a ver limpo");
        q("Source", "Fonte", "Fuente", "Quelle", "Source", "Fonte");
        q("Opens in", "Si apre in", "Se abre en", "Öffnet in", "S’ouvre dans", "Abre em");
        q("Any", "Qualsiasi", "Cualquiera", "Beliebig", "Tous", "Qualquer");
        q("Presentation mode", "Modalità presentazione", "Modo presentación", "Präsentationsmodus", "Mode présentation", "Modo de apresentação");
        q("Keep screen awake", "Tieni lo schermo acceso", "Mantener la pantalla encendida", "Bildschirm anlassen", "Garder l’écran allumé", "Manter o ecrã ligado");
        q("Start presentation now", "Avvia la presentazione ora", "Iniciar la presentación ahora", "Präsentation jetzt starten", "Lancer la présentation maintenant", "Iniciar a apresentação agora");
        q("Inputs card on Home", "Scheda Ingressi nella Home", "Tarjeta de Entradas en el inicio", "Eingänge-Karte auf der Startseite", "Carte Entrées sur l’accueil", "Cartão Entradas no início");
        q("This TV", "Questa TV", "Esta TV", "Dieser Fernseher", "Cette TV", "Esta TV");
        q("Updates", "Aggiornamenti", "Actualizaciones", "Aktualisierungen", "Mises à jour", "Atualizações");
        q("Weather source", "Fonte del meteo", "Fuente del tiempo", "Wetterquelle", "Source météo", "Fonte do tempo");
        q("If it can't be reached, the other sources are used.", "Se non è raggiungibile, si usano le altre fonti.", "Si no responde, se usan las otras fuentes.", "Ist sie nicht erreichbar, werden die anderen Quellen genutzt.", "Si elle est injoignable, les autres sources sont utilisées.", "Se não estiver acessível, usam-se as outras fontes.");
        q("Start with the TV", "Avvia con la TV", "Iniciar con la TV", "Mit dem Fernseher starten", "Démarrer avec la TV", "Iniciar com a TV");
        q("When the TV starts or wakes", "Quando la TV si avvia o si riattiva", "Cuando la TV arranca o se reactiva", "Wenn der Fernseher startet oder aufwacht", "Quand la TV démarre ou se réveille", "Quando a TV arranca ou desperta");
        q("Reduced — shorter, no drifting or swaying", "Ridotto — più breve, senza deriva né oscillazioni", "Reducido — más corto, sin deriva ni vaivén", "Reduziert — kürzer, ohne Driften und Schwanken", "Réduit — plus court, sans dérive ni balancement", "Reduzido — mais curto, sem deriva nem oscilação");
        q("Off — changes are instant", "Spento — i cambi sono istantanei", "Apagado — los cambios son instantáneos", "Aus — Wechsel sind sofort", "Désactivé — les changements sont instantanés", "Desligado — as mudanças são instantâneas");
        q("Full-screen carousel when idle", "Carosello a schermo intero a riposo", "Carrusel a pantalla completa en reposo", "Vollbild-Karussell im Leerlauf", "Carrousel plein écran au repos", "Carrossel em ecrã inteiro em repouso");
        q("Screensaver on Home", "Salvaschermo nella Home", "Salvapantallas en el inicio", "Bildschirmschoner auf der Startseite", "Économiseur d’écran sur l’accueil", "Protetor de ecrã no início");
        q("System screensaver", "Salvaschermo di sistema", "Salvapantallas del sistema", "System-Bildschirmschoner", "Économiseur d’écran système", "Protetor de ecrã do sistema");
        q("Lume carousel", "Carosello di Lume", "Carrusel de Lume", "Lume-Karussell", "Carrousel Lume", "Carrossel do Lume");
        q("Lume full-screen carousel", "Carosello di Lume a schermo intero", "Carrusel de Lume a pantalla completa", "Lume-Vollbild-Karussell", "Carrousel Lume plein écran", "Carrossel do Lume em ecrã inteiro");
        q("The TV's screensaver starts when Home is idle", "Il salvaschermo della TV parte quando la Home è ferma", "El salvapantallas de la TV arranca cuando el inicio está inactivo", "Der Bildschirmschoner startet, wenn die Startseite ruht", "L’économiseur d’écran de la TV démarre quand l’accueil est inactif", "O protetor de ecrã da TV arranca quando o início está parado");
        q("Home stays on and plays the carousel full screen instead", "La Home resta accesa e mostra il carosello a schermo intero", "El inicio sigue activo y muestra el carrusel a pantalla completa", "Die Startseite bleibt an und zeigt das Karussell im Vollbild", "L’accueil reste actif et affiche le carrousel en plein écran", "O início fica ativo e mostra o carrossel em ecrã inteiro");
        q("Start screensaver now", "Avvia il salvaschermo ora", "Iniciar el salvapantallas ahora", "Bildschirmschoner jetzt starten", "Lancer l’économiseur maintenant", "Iniciar o protetor de ecrã agora");
        q("This TV can't start the screensaver from an app", "Questa TV non può avviare il salvaschermo da un’app", "Esta TV no puede iniciar el salvapantallas desde una app", "Dieser Fernseher kann den Bildschirmschoner nicht aus einer App starten", "Cette TV ne peut pas lancer l’économiseur depuis une app", "Esta TV não pode iniciar o protetor de ecrã a partir de uma app");
        q("Reset app layout", "Ripristina la disposizione delle app", "Restablecer la disposición de apps", "App-Anordnung zurücksetzen", "Réinitialiser la disposition des apps", "Repor a disposição das apps");
        q("Layout reset", "Disposizione ripristinata", "Disposición restablecida", "Anordnung zurückgesetzt", "Disposition réinitialisée", "Disposição reposta");
        q("Lume is already the home app, so the Home button opens it.", "Lume è già l’app Home: il tasto Home la apre.", "Lume ya es la app de inicio, así que el botón Inicio la abre.", "Lume ist bereits die Start-App, die Home-Taste öffnet sie.", "Lume est déjà l’app d’accueil : la touche Accueil l’ouvre.", "O Lume já é a app inicial, por isso o botão Início abre-o.");
        q("When the TV turns on, open", "Quando la TV si accende, apri", "Cuando la TV se enciende, abrir", "Beim Einschalten öffnen", "À l’allumage de la TV, ouvrir", "Ao ligar a TV, abrir");
        q("Home button & Start with the TV", "Tasto Home e avvio con la TV", "Botón Inicio y arranque con la TV", "Home-Taste & Start mit dem Fernseher", "Touche Accueil et démarrage avec la TV", "Botão Início e arranque com a TV");
        q("Lume as the home screen", "Lume come schermata Home", "Lume como pantalla de inicio", "Lume als Startseite", "Lume comme écran d’accueil", "Lume como ecrã inicial");
        q("Choose a City…", "Scegli una città…", "Elige una ciudad…", "Stadt wählen…", "Choisir une ville…", "Escolha uma cidade…");
        q("Search by name (opens Settings)", "Cerca per nome (apre Impostazioni)", "Buscar por nombre (abre Ajustes)", "Nach Namen suchen (öffnet Einstellungen)", "Chercher par nom (ouvre Réglages)", "Pesquisar por nome (abre Definições)");
        q("Don’t Show Weather", "Non mostrare il meteo", "No mostrar el tiempo", "Wetter nicht anzeigen", "Ne pas afficher la météo", "Não mostrar o tempo");
        q("Found from your internet connection", "Trovata dalla tua connessione internet", "Encontrado por tu conexión a internet", "Über deine Internetverbindung gefunden", "Trouvée via votre connexion internet", "Encontrada pela sua ligação à internet");
        q("Getting the weather…", "Recupero il meteo…", "Obteniendo el tiempo…", "Wetter wird geladen…", "Récupération de la météo…", "A obter o tempo…");
        q("Use Presentation", "Usa la presentazione", "Usar presentación", "Präsentation verwenden", "Utiliser la présentation", "Usar apresentação");
        q("Adds a Presentation card to Home", "Aggiunge una scheda Presentazione alla Home", "Añade una tarjeta de Presentación al inicio", "Fügt der Startseite eine Präsentationskarte hinzu", "Ajoute une carte Présentation à l’accueil", "Adiciona um cartão Apresentação ao início");
        q("For shops, offices and signage", "Per negozi, uffici e insegne digitali", "Para tiendas, oficinas y cartelería", "Für Läden, Büros und Beschilderung", "Pour boutiques, bureaux et affichage", "Para lojas, escritórios e sinalética");
        q("Play as soon as the TV turns on", "Parte appena la TV si accende", "Se reproduce al encender la TV", "Startet, sobald der Fernseher an ist", "Démarre dès l’allumage de la TV", "Começa assim que a TV liga");
        q("No screensaver or sleep while presenting", "Niente salvaschermo né sospensione durante la presentazione", "Sin salvapantallas ni suspensión durante la presentación", "Kein Bildschirmschoner oder Standby während der Präsentation", "Pas d’économiseur ni de veille pendant la présentation", "Sem protetor de ecrã nem suspensão durante a apresentação");
        q("Needs Android TV 8 or later", "Richiede Android TV 8 o successivo", "Requiere Android TV 8 o posterior", "Benötigt Android TV 8 oder neuer", "Nécessite Android TV 8 ou plus récent", "Requer Android TV 8 ou posterior");
        q("Needs your OK — press OK to allow", "Serve il tuo OK — premi OK per consentire", "Necesita tu OK — pulsa OK para permitir", "Braucht dein OK — OK zum Erlauben", "Votre accord est requis — appuyez sur OK", "Precisa do seu OK — prima OK para permitir");
        q("Pick up shows and movies where you left off", "Riprendi programmi e film da dove eri rimasto", "Retoma series y películas donde las dejaste", "Setze Sendungen und Filme dort fort, wo du aufgehört hast", "Reprenez séries et films là où vous vous êtes arrêté", "Retome séries e filmes onde ficou");
        q("Rows your apps publish, like YouTube’s", "Le righe pubblicate dalle app, come quelle di YouTube", "Las filas que publican tus apps, como las de YouTube", "Reihen, die deine Apps veröffentlichen, wie bei YouTube", "Les rangées publiées par vos apps, comme celles de YouTube", "As linhas que as suas apps publicam, como as do YouTube");
        q("Press Back again to exit", "Premi di nuovo Indietro per uscire", "Pulsa Atrás otra vez para salir", "Zum Beenden erneut Zurück drücken", "Appuyez de nouveau sur Retour pour quitter", "Prima Voltar outra vez para sair");
        q("Nothing to present yet", "Ancora niente da presentare", "Aún no hay nada que presentar", "Noch nichts zu präsentieren", "Rien à présenter pour l’instant", "Ainda nada para apresentar");
        q("Skipping this slide", "Salto questa diapositiva", "Omitiendo esta diapositiva", "Diese Folie wird übersprungen", "Diapositive ignorée", "A saltar este diapositivo");
        q("Reduced — shorter, no drifting or swaying", "Ridotto — più breve, senza deriva né oscillazioni", "Reducido — más corto, sin deriva ni vaivén", "Reduziert — kürzer, ohne Driften und Schwanken", "Réduit — plus court, sans dérive ni balancement", "Reduzido — mais curto, sem deriva nem oscilação");
        q("%d%% used  \u00b7  Clean", "%d%% in uso  \u00b7  Pulisci", "%d%% usada  \u00b7  Limpiar", "%d%% belegt  \u00b7  Bereinigen", "%d%% utilis\u00e9e  \u00b7  Nettoyer", "%d%% em uso  \u00b7  Limpar");
        q("Excellent", "Ottimo", "Excelente", "Sehr gut", "Excellent", "Excelente");
        q("Good", "Buono", "Bueno", "Gut", "Bon", "Bom");
        q("Weak", "Debole", "D\u00e9bil", "Schwach", "Faible", "Fraco");
        q("Colour", "Colore", "Color", "Farbe", "Couleur", "Cor");
        q("TV health", "Salute della TV", "Salud de la TV", "Zustand des Fernsehers", "Santé de la TV", "Saúde da TV");
        q("In great shape", "In piena forma", "En plena forma", "Topfit", "En pleine forme", "Em plena forma");
        q("Running well", "Va bene", "Va bien", "Läuft gut", "Ça va", "Vai bem");
        q("Low on memory", "Poca memoria libera", "Poca memoria libre", "Wenig freier Speicher", "Peu de mémoire libre", "Pouca memória livre");
        q("MB free", "MB liberi", "MB libres", "MB frei", "Mo libres", "MB livres");
        q("Free up memory", "Libera memoria", "Liberar memoria", "Speicher freigeben", "Libérer la mémoire", "Libertar memória");
        q("Freeing\u2026", "Libero\u2026", "Liberando\u2026", "Gebe frei\u2026", "Libération\u2026", "A libertar\u2026");
        q("Freed %d MB", "Liberati %d MB", "Liberados %d MB", "%d MB freigegeben", "%d Mo libérés", "Libertados %d MB");
        q("Nothing more to free", "Non c\u2019è altro da liberare", "No hay más que liberar", "Nichts mehr freizugeben", "Plus rien à libérer", "Nada mais a libertar");
        q("Profile", "Profilo", "Perfil", "Profil", "Profil", "Perfil");
        q("Amazon extras", "Extra di Amazon", "Extras de Amazon", "Amazon-Extras", "Extras Amazon", "Extras da Amazon");
        q("All turned off", "Tutti disattivati", "Todos desactivados", "Alle ausgeschaltet", "Tous désactivés", "Todos desativados");
        q("%d still on. TV Doctor on a computer turns them off safely.", "%d ancora attivi. TV Doctor sul computer li disattiva in sicurezza.", "%d siguen activos. TV Doctor en el ordenador los desactiva con seguridad.", "%d noch an. TV Doctor am Computer schaltet sie sicher aus.", "%d encore actifs. TV Doctor sur l\u2019ordinateur les désactive en toute sécurité.", "%d ainda ativos. O TV Doctor no computador desativa-os em segurança.");
        q("%s free", "%s liberi", "%s libres", "%s frei", "%s libres", "%s livres");
        q("HOURLY FORECAST", "PREVISIONI ORARIE", "PRONÓSTICO POR HORAS", "STUNDENVORHERSAGE", "PRÉVISIONS HORAIRES", "PREVISÃO HORÁRIA");
        q("5-DAY FORECAST", "PREVISIONI A 5 GIORNI", "PRONÓSTICO A 5 DÍAS", "5-TAGE-VORHERSAGE", "PRÉVISIONS À 5 JOURS", "PREVISÃO A 5 DIAS");
        q("Humidity", "Umidità", "Humedad", "Luftfeuchtigkeit", "Humidité", "Humidade");
        q("Wind", "Vento", "Viento", "Wind", "Vent", "Vento");
        q("Updated", "Aggiornato", "Actualizado", "Aktualisiert", "Mis à jour", "Atualizado");
        q("Markets", "Mercati", "Mercados", "Märkte", "Marchés", "Mercados");
        q("Choose indices", "Scegli indici", "Elegir índices", "Indizes wählen", "Choisir les indices", "Escolher índices");
        q("Refresh", "Aggiorna", "Actualizar", "Aktualisieren", "Actualiser", "Atualizar");
        q("Loading\u2026", "Caricamento\u2026", "Cargando\u2026", "Lädt\u2026", "Chargement\u2026", "A carregar\u2026");
        q("News", "Notizie", "Noticias", "Nachrichten", "Actualités", "Notícias");
        q("Newsrooms", "Testate", "Medios", "Redaktionen", "Rédactions", "Redações");
        q("Weather", "Meteo", "Tiempo", "Wetter", "Météo", "Tempo");
        q("Not set", "Non impostato", "Sin configurar", "Nicht eingerichtet", "Non défini", "Não definido");
        q("No news right now. Check the connection.", "Nessuna notizia al momento. Controlla la connessione.", "No hay noticias ahora. Revisa la conexión.", "Gerade keine Nachrichten. Prüfe die Verbindung.", "Aucune actualité pour l\u2019instant. Vérifiez la connexion.", "Sem notícias agora. Verifique a ligação.");
        q("Choose the newsrooms to show. OK switches one on or off.", "Scegli le testate da mostrare. OK le attiva o disattiva.", "Elige los medios que mostrar. OK activa o desactiva uno.", "Wähle die Redaktionen. OK schaltet eine ein oder aus.", "Choisissez les rédactions à afficher. OK en active ou désactive une.", "Escolha as redações a mostrar. OK ativa ou desativa uma.");
        q("Gold", "Oro", "Oro", "Gold", "Or", "Ouro");
        q("Oil (WTI)", "Petrolio (WTI)", "Petróleo (WTI)", "Öl (WTI)", "Pétrole (WTI)", "Petróleo (WTI)");
        q("World", "Mondo", "Mundo", "Welt", "Monde", "Mundo");
        q("Just now", "Adesso", "Ahora mismo", "Gerade eben", "À l\u2019instant", "Agora mesmo");
        q("%d min ago", "%d min fa", "hace %d min", "vor %d Min.", "il y a %d min", "há %d min");
        q("%d h ago", "%d h fa", "hace %d h", "vor %d Std.", "il y a %d h", "há %d h");
        q("Yesterday", "Ieri", "Ayer", "Gestern", "Hier", "Ontem");
        q("%d days ago", "%d giorni fa", "hace %d días", "vor %d Tagen", "il y a %d jours", "há %d dias");
        q("Rest", "Riposo", "Reposo", "Ruhe", "Veille", "Repouso");
        q("My list", "La mia lista", "Mi lista", "Meine Liste", "Ma liste", "A minha lista");
        q("Trending now", "In tendenza", "Tendencias", "Im Trend", "Tendances", "Em alta");
        q("Everything", "Tutto", "Todo", "Alles", "Tout", "Tudo");
        q("Show", "Mostra", "Mostrar", "Anzeigen", "Afficher", "Mostrar");
        q("No newsroom with local news for %s yet. Choose another view.", "Nessuna testata ha ancora notizie locali per %s. Scegli un\u2019altra vista.", "Ningún medio tiene aún noticias locales de %s. Elige otra vista.", "Noch keine Redaktion mit lokalen Nachrichten für %s. Wähle eine andere Ansicht.", "Aucune rédaction n\u2019a encore d\u2019actualités locales pour %s. Choisissez une autre vue.", "Ainda nenhuma redação tem notícias locais para %s. Escolha outra vista.");
        q("My place", "Il mio luogo", "Mi lugar", "Mein Ort", "Mon lieu", "O meu local");
        q("Add a city", "Aggiungi una città", "Añadir una ciudad", "Stadt hinzufügen", "Ajouter une ville", "Adicionar uma cidade");
        q("Edit", "Modifica", "Editar", "Bearbeiten", "Modifier", "Editar");
        q("Choose a city", "Scegli una città", "Elige una ciudad", "Stadt wählen", "Choisir une ville", "Escolha uma cidade");
        q("City name", "Nome della città", "Nombre de la ciudad", "Name der Stadt", "Nom de la ville", "Nome da cidade");
        q("No city found. Check the name and the connection.", "Nessuna città trovata. Controlla il nome e la connessione.", "No se encontró la ciudad. Revisa el nombre y la conexión.", "Keine Stadt gefunden. Prüfe Name und Verbindung.", "Aucune ville trouvée. Vérifiez le nom et la connexion.", "Nenhuma cidade encontrada. Verifique o nome e a ligação.");
        q("Add the cities you care about: home, family, a place you are going to.", "Aggiungi le città che ti interessano: casa, famiglia, un posto dove stai andando.", "Añade las ciudades que te importan: casa, familia, un lugar al que vas.", "Füge Städte hinzu, die dir wichtig sind: Zuhause, Familie, ein Reiseziel.", "Ajoutez les villes qui comptent : maison, famille, un lieu où vous allez.", "Adicione as cidades que lhe interessam: casa, família, um sítio para onde vai.");
        q("Shown on Home", "In home", "En el inicio", "Auf der Startseite", "Sur l\u2019accueil", "No início");
        q("Use my location", "Usa la mia posizione", "Usar mi ubicación", "Meinen Standort verwenden", "Utiliser ma position", "Usar a minha localização");
        q("OK on a city shows it on Home.", "OK su una città per mostrarla nella Home.", "OK en una ciudad para mostrarla en el inicio.", "OK auf einer Stadt zeigt sie auf der Startseite.", "OK sur une ville pour l\u2019afficher sur l\u2019accueil.", "OK numa cidade para a mostrar no início.");
        q("Open in \u2026", "Apri in \u2026", "Abrir en \u2026", "Öffnen in \u2026", "Ouvrir dans \u2026", "Abrir em \u2026");
        q("Don\u2019t show ", "Non mostrare ", "No mostrar ", "Nicht anzeigen: ", "Ne pas afficher ", "Não mostrar ");
        q("Removed ", "Rimosso: ", "Quitado: ", "Entfernt: ", "Retiré : ", "Removido: ");
        q("Closed ", "Chiusa: ", "Cerrada: ", "Geschlossen: ", "Fermée : ", "Fechada: ");
        q("Can't open ", "Impossibile aprire ", "No se puede abrir ", "Kann nicht geöffnet werden: ", "Impossible d\u2019ouvrir ", "Não é possível abrir ");
        q("Can't switch to ", "Impossibile passare a ", "No se puede cambiar a ", "Wechsel nicht möglich zu ", "Impossible de passer à ", "Não é possível mudar para ");
        q("Delete \u201C", "Eliminare \u201C", "¿Eliminar \u201C", "Löschen \u201E", "Supprimer \u00AB ", "Eliminar \u201C");
        q("Emoji", "Emoji", "Emoji", "Emoji", "Emoji", "Emoji");
        q("Activate device admin app?", "Attivare l\u2019app amministratore del dispositivo?", "¿Activar la app de administración del dispositivo?", "Geräteadministrator-App aktivieren?", "Activer l\u2019app d\u2019administration de l\u2019appareil ?", "Ativar a app de administração do dispositivo?");
        q("Continue Watching reads your apps' Watch Next lists, which need Android TV 8 or later.", "Continua a guardare legge le liste \u201CProssimo\u201D delle tue app, che richiedono Android TV 8 o successivo.", "Seguir viendo lee las listas \u201CA continuación\u201D de tus apps, que requieren Android TV 8 o posterior.", "Weiterschauen liest die Watch-Next-Listen deiner Apps; dafür ist Android TV 8 oder neuer nötig.", "Reprendre la lecture lit les listes \u00AB À suivre \u00BB de vos apps, qui nécessitent Android TV 8 ou plus récent.", "Continuar a ver lê as listas \u201CA seguir\u201D das suas apps, que requerem Android TV 8 ou posterior.");
        q("Lume switch", "Interruttore di Lume", "Interruptor de Lume", "Lume-Schalter", "Interrupteur de Lume", "Interruptor do Lume");
        q("Set as default home app?", "Impostare come app Home predefinita?", "¿Establecer como app de inicio predeterminada?", "Als Standard-Start-App festlegen?", "Définir comme app d\u2019accueil par défaut ?", "Definir como app inicial predefinida?");
        q("It lets Lume put the TV to sleep (and handle the Home button and Start with the TV).", "Permette a Lume di mettere la TV in sospensione (e gestire il tasto Home e l\u2019avvio con la TV).", "Permite a Lume poner la TV en reposo (y gestionar el botón Inicio y el arranque con la TV).", "Damit kann Lume den Fernseher in den Standby schicken (und die Home-Taste sowie den Start mit dem Fernseher steuern).", "Cela permet à Lume de mettre la TV en veille (et de gérer la touche Accueil et le démarrage avec la TV).", "Permite ao Lume pôr a TV em suspensão (e gerir o botão Início e o arranque com a TV).");
        q("Lets Lume turn the screen off for Power Off and the Sleep Timer. It can't do anything else.", "Permette a Lume di spegnere lo schermo per Spegni e per il timer. Non può fare altro.", "Permite a Lume apagar la pantalla para Apagar y el temporizador. No puede hacer nada más.", "Lume darf den Bildschirm für Ausschalten und den Schlaftimer abschalten. Mehr kann es nicht.", "Permet à Lume d\u2019éteindre l\u2019écran pour Éteindre et la minuterie. Il ne peut rien faire d\u2019autre.", "Permite ao Lume desligar o ecrã para Desligar e o temporizador. Não pode fazer mais nada.");
        q("Do Not Disturb can't work here.", "Non disturbare qui non può funzionare.", "No molestar no puede funcionar aquí.", "Nicht stören funktioniert hier nicht.", "Ne pas déranger ne peut pas fonctionner ici.", "Não incomodar não pode funcionar aqui.");
        q("Show profiles on wake", "Mostra i profili al risveglio", "Mostrar perfiles al despertar", "Profile beim Aufwachen zeigen", "Afficher les profils au réveil", "Mostrar perfis ao despertar");
        q("Stremio (not installed)", "Stremio (non installato)", "Stremio (no instalado)", "Stremio (nicht installiert)", "Stremio (non installé)", "Stremio (não instalado)");
        q("Local news for", "Notizie locali per", "Noticias locales de", "Lokale Nachrichten für", "Actualités locales pour", "Notícias locais de");
        q("Local news follows the weather location (Settings \u2192 Weather \u2192 Location).", "Le notizie locali seguono il luogo del meteo (Impostazioni \u2192 Meteo \u2192 Luogo).", "Las noticias locales siguen la ubicación del tiempo (Ajustes \u2192 Tiempo \u2192 Ubicación).", "Lokale Nachrichten folgen dem Wetterort (Einstellungen \u2192 Wetter \u2192 Ort).", "Les actualités locales suivent le lieu de la météo (Réglages \u2192 Météo \u2192 Lieu).", "As notícias locais seguem o local do tempo (Definições \u2192 Tempo \u2192 Local).");
        q("Update news every", "Aggiorna le notizie ogni", "Actualizar noticias cada", "Nachrichten aktualisieren alle", "Actualiser les actualités toutes les", "Atualizar notícias a cada");
        q("Every ", "Ogni ", "Cada ", "Alle ", "Toutes les ", "A cada ");
        q("The carousel will refresh on Home", "Il carosello si aggiornerà nella Home", "El carrusel se actualizará en el inicio", "Das Karussell wird auf der Startseite aktualisiert", "Le carrousel sera actualisé sur l\u2019accueil", "O carrossel será atualizado no início");
        q("None of your apps publish rows on this TV yet. Open them once and check again.", "Nessuna delle tue app pubblica ancora righe su questa TV. Aprile una volta e ricontrolla.", "Ninguna de tus apps publica filas en esta TV todavía. Ábrelas una vez y vuelve a comprobar.", "Keine deiner Apps veröffentlicht hier schon Reihen. Öffne sie einmal und prüfe erneut.", "Aucune de vos apps ne publie encore de rangées sur cette TV. Ouvrez-les une fois et revérifiez.", "Nenhuma das suas apps publica ainda linhas nesta TV. Abra-as uma vez e verifique de novo.");
        q("Apps in the carousel", "App nel carosello", "Apps en el carrusel", "Apps im Karussell", "Apps dans le carrousel", "Apps no carrossel");
        q("Presentation is on. Send content from a browser.", "La presentazione è attiva. Invia contenuti da un browser.", "La presentación está activa. Envía contenido desde un navegador.", "Die Präsentation ist an. Sende Inhalte aus einem Browser.", "La présentation est activée. Envoyez du contenu depuis un navigateur.", "A apresentação está ativa. Envie conteúdo a partir de um navegador.");
        q("Address & pairing code", "Indirizzo e codice di abbinamento", "Dirección y código de emparejamiento", "Adresse und Kopplungscode", "Adresse et code d\u2019appairage", "Endereço e código de emparelhamento");
        q("Automatic \u00b7 ", "Automatico \u00b7 ", "Automático \u00b7 ", "Automatisch \u00b7 ", "Automatique \u00b7 ", "Automático \u00b7 ");
        q("Make Lume the home screen\u2026", "Rendi Lume la schermata Home\u2026", "Hacer de Lume la pantalla de inicio\u2026", "Lume zur Startseite machen\u2026", "Faire de Lume l\u2019écran d\u2019accueil\u2026", "Tornar o Lume o ecrã inicial\u2026");
        q("When the TV starts", "Quando la TV si avvia", "Cuando la TV arranca", "Wenn der Fernseher startet", "Quand la TV démarre", "Quando a TV arranca");
        q("When the TV starts or wakes from standby", "Quando la TV si avvia o esce dalla sospensione", "Cuando la TV arranca o sale del reposo", "Wenn der Fernseher startet oder aus dem Standby aufwacht", "Quand la TV démarre ou sort de veille", "Quando a TV arranca ou sai de suspensão");
        q("Open it", "Aprila", "Ábrela", "Öffnen", "L\u2019ouvrir", "Abri-la");
        q("Waking into it", "Al risveglio", "Al despertar", "Beim Aufwachen", "Au réveil", "Ao despertar");
        q("Presentation starts instead", "Parte invece la presentazione", "En su lugar empieza la presentación", "Stattdessen startet die Präsentation", "La présentation démarre à la place", "Em vez disso começa a apresentação");
        q("After ", "Dopo ", "Después de ", "Nach ", "Après ", "Após ");
        q("Full-screen carousel", "Carosello a schermo intero", "Carrusel a pantalla completa", "Vollbild-Karussell", "Carrousel plein écran", "Carrossel em ecrã inteiro");
        q("Movies & TV in search", "Film e TV nella ricerca", "Películas y TV en la búsqueda", "Filme & TV in der Suche", "Films et TV dans la recherche", "Filmes e TV na pesquisa");
        q("Continue Watching (apps\u2019 Watch Next)", "Continua a guardare (Prossimo delle app)", "Seguir viendo (A continuación de las apps)", "Weiterschauen (Watch Next der Apps)", "Reprendre la lecture (À suivre des apps)", "Continuar a ver (A seguir das apps)");
        q("Based on", "Basato su", "Basado en", "Basierend auf", "Basé sur", "Baseado em");
        q("Choose the screensaver in TV Settings \u2192 Device Preferences \u2192 Screen saver", "Scegli il salvaschermo in Impostazioni TV \u2192 Preferenze dispositivo \u2192 Salvaschermo", "Elige el salvapantallas en Ajustes de la TV \u2192 Preferencias del dispositivo \u2192 Salvapantallas", "Wähle den Bildschirmschoner unter TV-Einstellungen \u2192 Geräteeinstellungen \u2192 Bildschirmschoner", "Choisissez l\u2019économiseur dans Réglages TV \u2192 Préférences de l\u2019appareil \u2192 Économiseur d\u2019écran", "Escolha o protetor de ecrã em Definições da TV \u2192 Preferências do dispositivo \u2192 Protetor de ecrã");
        q("Recommendation source", "Fonte dei consigli", "Fuente de recomendaciones", "Empfehlungsquelle", "Source des recommandations", "Fonte das recomendações");
        q("Choose City\u2026", "Scegli una città\u2026", "Elige una ciudad\u2026", "Stadt wählen\u2026", "Choisir une ville\u2026", "Escolha uma cidade\u2026");
        q("Couldn't search. Check the internet connection.", "Impossibile cercare. Controlla la connessione a internet.", "No se pudo buscar. Revisa la conexión a internet.", "Suche nicht möglich. Prüfe die Internetverbindung.", "Recherche impossible. Vérifiez la connexion internet.", "Não foi possível pesquisar. Verifique a ligação à internet.");
        q("Turn on Presentation mode first", "Attiva prima la modalità presentazione", "Activa primero el modo presentación", "Schalte zuerst den Präsentationsmodus ein", "Activez d\u2019abord le mode présentation", "Ative primeiro o modo de apresentação");
        q("Send to this TV", "Invia a questa TV", "Enviar a esta TV", "An diesen Fernseher senden", "Envoyer à cette TV", "Enviar para esta TV");
        q("Keep at least one service", "Tieni almeno un servizio", "Conserva al menos un servicio", "Behalte mindestens einen Dienst", "Gardez au moins un service", "Mantenha pelo menos um serviço");
        q("Languages (none = any)", "Lingue (nessuna = tutte)", "Idiomas (ninguno = todos)", "Sprachen (keine = alle)", "Langues (aucune = toutes)", "Idiomas (nenhum = todos)");
        q("Not streaming in your country right now.", "In questo momento non è in streaming nel tuo paese.", "Ahora mismo no está en streaming en tu país.", "Derzeit in deinem Land nicht im Streaming.", "Pas disponible en streaming dans votre pays pour l\u2019instant.", "Neste momento não está em streaming no seu país.");
        q("Get ", "Ottieni ", "Obtener ", "Holen: ", "Obtenir ", "Obter ");
        q("Looks like ", "Sembra ", "Parece ", "Sieht aus wie ", "On dirait ", "Parece ");
        q("Webpage failed to load: ", "Caricamento della pagina web non riuscito: ", "No se pudo cargar la página web: ", "Webseite konnte nicht geladen werden: ", "Échec du chargement de la page web : ", "Falha ao carregar a página web: ");
        q("Add images, videos or webpages from a phone or computer on the same Wi-Fi.", "Aggiungi immagini, video o pagine web da un telefono o computer sulla stessa Wi-Fi.", "Añade imágenes, vídeos o páginas web desde un teléfono u ordenador en la misma Wi-Fi.", "Füge Bilder, Videos oder Webseiten von einem Telefon oder Computer im selben WLAN hinzu.", "Ajoutez des images, vidéos ou pages web depuis un téléphone ou un ordinateur sur le même Wi-Fi.", "Adicione imagens, vídeos ou páginas web a partir de um telefone ou computador na mesma Wi-Fi.");
        q("Scan the code or open this address on a phone or computer on the same Wi-Fi.", "Inquadra il codice o apri questo indirizzo da un telefono o computer sulla stessa Wi-Fi.", "Escanea el código o abre esta dirección desde un teléfono u ordenador en la misma Wi-Fi.", "Scanne den Code oder öffne diese Adresse auf einem Telefon oder Computer im selben WLAN.", "Scannez le code ou ouvrez cette adresse depuis un téléphone ou un ordinateur sur le même Wi-Fi.", "Leia o código ou abra este endereço num telefone ou computador na mesma Wi-Fi.");
        q("Video failed: ", "Video non riuscito: ", "Falló el vídeo: ", "Video fehlgeschlagen: ", "Échec de la vidéo : ", "Falha no vídeo: ");
        q("Crime", "Crimine", "Crimen", "Krimi", "Crime", "Crime");
        q("Fantasy", "Fantasy", "Fantasía", "Fantasy", "Fantastique", "Fantasia");
        q("Horror", "Horror", "Terror", "Horror", "Horreur", "Terror");
        q("Thriller", "Thriller", "Suspense", "Thriller", "Thriller", "Suspense");
        q("Romance", "Romantico", "Romance", "Romanze", "Romance", "Romance");
        q("Sci-Fi", "Fantascienza", "Ciencia ficción", "Science-Fiction", "Science-fiction", "Ficção científica");
        q("Western", "Western", "Western", "Western", "Western", "Western");
        q("Reality", "Reality", "Telerrealidad", "Reality-TV", "Téléréalité", "Reality show");
        q("Sci-Fi & Fantasy", "Fantascienza e fantasy", "Ciencia ficción y fantasía", "Science-Fiction & Fantasy", "Science-fiction et fantastique", "Ficção científica e fantasia");
        q("Pink", "Rosa", "Rosa", "Rosa", "Rose", "Rosa");
        q("Mint", "Menta", "Menta", "Mint", "Menthe", "Menta");
        q("Lemon", "Limone", "Limón", "Zitrone", "Citron", "Limão");
        q("Lavender", "Lavanda", "Lavanda", "Lavendel", "Lavande", "Lavanda");
        q("Peach", "Pesca", "Melocotón", "Pfirsich", "Pêche", "Pêssego");
        q("Sky", "Cielo", "Cielo", "Himmel", "Ciel", "Céu");
        q("Silver", "Argento", "Plata", "Silber", "Argent", "Prata");
        q("Default", "Predefinito", "Predeterminado", "Standard", "Par défaut", "Predefinido");
        q("Subscription", "Abbonamento", "Suscripción", "Abo", "Abonnement", "Subscrição");
        q("to allow", "da consentire", "por permitir", "zu erlauben", "à autoriser", "por permitir");
        q("newsrooms", "testate", "medios", "Redaktionen", "rédactions", "redações");
        q("sources", "fonti", "fuentes", "Quellen", "sources", "fontes");
        q("selected", "selezionate", "seleccionados", "ausgewählt", "sélectionnés", "selecionados");
        q("About", "Informazioni", "Acerca de", "Info", "À propos", "Acerca de");
        q("TV Settings", "Impostazioni TV", "Ajustes de la TV", "TV-Einstellungen", "Réglages TV", "Definições da TV");
        q("Needs Lume\u2019s switch\u2026", "Serve l\u2019interruttore di Lume\u2026", "Necesita el interruptor de Lume\u2026", "Braucht Lumes Schalter\u2026", "Nécessite l\u2019interrupteur de Lume\u2026", "Precisa do interruptor do Lume\u2026");
        q("Prices can be delayed. For information only, not financial advice.", "I prezzi possono essere in ritardo. Solo a scopo informativo, non è consulenza finanziaria.", "Los precios pueden tener retraso. Solo informativo, no es asesoramiento financiero.", "Kurse können verzögert sein. Nur zur Information, keine Anlageberatung.", "Les cours peuvent être différés. À titre informatif, ce n’est pas un conseil financier.", "Os preços podem ter atraso. Apenas informativo, não é aconselhamento financeiro.");
        q("Sunny", "Soleggiato", "Soleado", "Sonnig", "Ensoleillé", "Ensolarado");
        q("Cloudy", "Nuvoloso", "Nublado", "Bewölkt", "Nuageux", "Nublado");
        q("Partly Cloudy", "Parzialmente nuvoloso", "Parcialmente nublado", "Teilweise bewölkt", "Partiellement nuageux", "Parcialmente nublado");
        q("Rain", "Pioggia", "Lluvia", "Regen", "Pluie", "Chuva");
        q("Snow", "Neve", "Nieve", "Schnee", "Neige", "Neve");
        q("Foggy", "Nebbia", "Niebla", "Nebel", "Brouillard", "Nevoeiro");
        q("Thunderstorms", "Temporali", "Tormentas", "Gewitter", "Orages", "Tempestades");

        // ---- this version's own features
        q("Optimize your TV", "Ottimizza la tua TV", "Optimiza tu TV", "Optimiere deinen Fernseher", "Optimisez votre TV", "Otimize sua TV");
        q("I measured this device and chose the best settings for smoothness and stability. You can change them any time.",
          "Ho misurato questo dispositivo e scelto le impostazioni migliori per fluidità e stabilità. Puoi cambiarle quando vuoi.",
          "He medido este dispositivo y elegido los mejores ajustes de fluidez y estabilidad. Puedes cambiarlos cuando quieras.",
          "Ich habe dieses Gerät gemessen und die besten Einstellungen für flüssigen und stabilen Betrieb gewählt. Du kannst sie jederzeit ändern.",
          "J'ai mesuré cet appareil et choisi les meilleurs réglages de fluidité et de stabilité. Vous pouvez les modifier à tout moment.",
          "Medi este dispositivo e escolhi os melhores ajustes de fluidez e estabilidade. Você pode alterá-los quando quiser.");
        q("Device", "Dispositivo", "Dispositivo", "Gerät", "Appareil", "Dispositivo");
        q("Storage", "Spazio", "Almacenamiento", "Speicher", "Stockage", "Armazenamento");
        q("Connection", "Connessione", "Conexión", "Verbindung", "Connexion", "Conexão");
        q("Automatic optimization", "Ottimizzazione automatica", "Optimización automática", "Automatische Optimierung", "Optimisation automatique", "Otimização automática");
        q("On: adapts to this device", "Attiva: si adatta a questo dispositivo", "Activa: se adapta a este dispositivo", "An: passt sich an dieses Gerät an", "Activée : s'adapte à cet appareil", "Ativa: adapta-se a este dispositivo");
        q("Off: animations and images at their best", "Spenta: animazioni e immagini al massimo", "Desactivada: animaciones e imágenes al máximo", "Aus: Animationen und Bilder in voller Qualität", "Désactivée : animations et images au maximum", "Desativada: animações e imagens no máximo");
        q("%s total, %s free", "%s in totale, %s liberi", "%s en total, %s libres", "%s gesamt, %s frei", "%s au total, %s libres", "%s no total, %s livres");
        q("%s free. Images use at most %d MB", "%s liberi. Le immagini occupano al massimo %d MB", "%s libres. Las imágenes ocupan como máximo %d MB", "%s frei. Bilder belegen höchstens %d MB", "%s libres. Les images occupent au plus %d Mo", "%s livres. As imagens ocupam no máximo %d MB");
        q(". Light animations and compact images", ". Animazioni leggere e immagini più compatte", ". Animaciones ligeras e imágenes compactas", ". Leichte Animationen und kompakte Bilder", ". Animations légères et images compactes", ". Animações leves e imagens compactas");
        q("Essential", "Essenziale", "Esencial", "Essenziell", "Essentiel", "Essencial");
        q("Balanced", "Equilibrato", "Equilibrado", "Ausgewogen", "Équilibré", "Equilibrado");
        q("profile %s", "profilo %s", "perfil %s", "Profil %s", "profil %s", "perfil %s");
        q("Ethernet cable: the most stable choice", "Cavo Ethernet: la scelta più stabile", "Cable Ethernet: la opción más estable", "Ethernet-Kabel: die stabilste Wahl", "Câble Ethernet : le choix le plus stable", "Cabo Ethernet: a escolha mais estável");
        q("No connection detected", "Nessuna connessione rilevata", "No se detecta conexión", "Keine Verbindung erkannt", "Aucune connexion détectée", "Nenhuma conexão detectada");
        q("Wi-Fi %s%s, signal %s", "Wi-Fi %s%s, segnale %s", "Wi-Fi %s%s, señal %s", "WLAN %s%s, Signal %s", "Wi-Fi %s%s, signal %s", "Wi-Fi %s%s, sinal %s");
        q("excellent", "ottimo", "excelente", "ausgezeichnet", "excellent", "excelente");
        q("good", "buono", "bueno", "gut", "bon", "bom");
        q("weak", "debole", "débil", "schwach", "faible", "fraco");
        q("very weak", "molto debole", "muy débil", "sehr schwach", "très faible", "muito fraco");
        q(". If your router has a 5 GHz network, use it", ". Se il router ha la rete 5 GHz, usala", ". Si tu router tiene red de 5 GHz, úsala", ". Wenn dein Router ein 5-GHz-Netz hat, nutze es", ". Si votre routeur a un réseau 5 GHz, utilisez-le", ". Se o roteador tiver rede de 5 GHz, use-a");
        q(". Move the router closer or use an extender", ". Avvicina il router o usa un ripetitore", ". Acerca el router o usa un repetidor", ". Router näher stellen oder Repeater nutzen", ". Rapprochez le routeur ou utilisez un répéteur", ". Aproxime o roteador ou use um repetidor");
        q("Card style: %s", "Stile scheda: %s", "Estilo de tarjeta: %s", "Kartenstil: %s", "Style de carte : %s", "Estilo do cartão: %s");
        q("Auto", "Automatico", "Automático", "Automatisch", "Automatique", "Automático");
        q("Icon", "Icona", "Icono", "Symbol", "Icône", "Ícone");
        q("Monogram", "Monogramma", "Monograma", "Monogramm", "Monogramme", "Monograma");
        q("Banner", "Banner", "Banner", "Banner", "Bannière", "Banner");
        q("Open the Gallery", "Apri la Galleria", "Abrir la Galería", "Galerie öffnen", "Ouvrir la Galerie", "Abrir a Galeria");
        q("Art Gallery", "Galleria d'arte", "Galería de arte", "Kunstgalerie", "Galerie d'art", "Galeria de arte");
        q("Landscape", "Paesaggio", "Paisaje", "Landschaft", "Paysage", "Paisagem");
        q("Sea", "Mare", "Mar", "Meer", "Mer", "Mar");
        q("City", "Città", "Ciudad", "Stadt", "Ville", "Cidade");
        q("Nature", "Natura", "Naturaleza", "Natur", "Nature", "Natureza");
        q("Portrait", "Ritratto", "Retrato", "Porträt", "Portrait", "Retrato");
        q("Share to this TV", "Condividi su questa TV", "Compartir en esta TV", "Auf diesem Fernseher teilen", "Partager sur cette TV", "Compartilhar nesta TV");
        q("Looking for the receiver\u2026", "Cerco il ricevitore\u2026", "Buscando el receptor\u2026", "Suche den Empf\u00E4nger\u2026", "Recherche du r\u00E9cepteur\u2026", "Procurando o receptor\u2026");
        q("Ready: this TV appears in AirPlay", "Pronto: questa TV compare in AirPlay", "Listo: esta TV aparece en AirPlay", "Bereit: Dieser Fernseher erscheint in AirPlay", "Pr\u00EAt : cette TV appara\u00EEt dans AirPlay", "Pronto: esta TV aparece no AirPlay");
        q("Receiver not running", "Ricevitore non attivo", "Receptor no activo", "Empf\u00E4nger nicht aktiv", "R\u00E9cepteur inactif", "Receptor inativo");
        q("Receiver not installed", "Ricevitore non installato", "Receptor no instalado", "Empf\u00E4nger nicht installiert", "R\u00E9cepteur non install\u00E9", "Receptor n\u00E3o instalado");
        q("Same Wi-Fi", "Stessa Wi-Fi", "Misma Wi-Fi", "Gleiches WLAN", "M\u00EAme Wi-Fi", "Mesma Wi-Fi");
        q("Your iPhone and this TV must be on the same Wi-Fi network.", "iPhone e TV devono essere sulla stessa rete Wi-Fi.", "El iPhone y esta TV deben estar en la misma red Wi-Fi.", "iPhone und Fernseher m\u00FCssen im selben WLAN sein.", "L'iPhone et cette TV doivent \u00EAtre sur le m\u00EAme r\u00E9seau Wi-Fi.", "O iPhone e esta TV devem estar na mesma rede Wi-Fi.");
        q("Music", "Musica", "M\u00FAsica", "Musik", "Musique", "M\u00FAsica");
        q("Open Apple Music, tap the AirPlay icon and choose %s.", "Apri Apple Music, tocca l'icona AirPlay e scegli %s.", "Abre Apple Music, toca el icono de AirPlay y elige %s.", "\u00D6ffne Apple Music, tippe auf das AirPlay-Symbol und w\u00E4hle %s.", "Ouvrez Apple Music, touchez l'ic\u00F4ne AirPlay et choisissez %s.", "Abra o Apple Music, toque no \u00EDcone do AirPlay e escolha %s.");
        q("Screen", "Schermo", "Pantalla", "Bildschirm", "\u00C9cran", "Tela");
        q("Control Center, then Screen Mirroring, then choose %s.", "Centro di controllo, poi Duplica schermo, poi scegli %s.", "Centro de control, luego Duplicar pantalla, y elige %s.", "Kontrollzentrum, dann Bildschirmspiegelung, dann %s w\u00E4hlen.", "Centre de contr\u00F4le, puis Recopie de l'\u00E9cran, puis choisissez %s.", "Central de controle, depois Espelhamento de tela, e escolha %s.");
        q("The first time, the connection can fail once: just try again.", "La prima volta la connessione pu\u00F2 fallire: riprova.", "La primera vez la conexi\u00F3n puede fallar: vuelve a intentarlo.", "Beim ersten Mal kann die Verbindung scheitern: einfach nochmal versuchen.", "La premi\u00E8re fois, la connexion peut \u00E9chouer : r\u00E9essayez.", "Na primeira vez a conex\u00E3o pode falhar: tente de novo.");
        q("Open the receiver", "Apri il ricevitore", "Abrir el receptor", "Empf\u00E4nger \u00F6ffnen", "Ouvrir le r\u00E9cepteur", "Abrir o receptor");
    }

    /** The text in the device language, or the same text when it is not translated. */
    private static final java.util.regex.Pattern NUM = java.util.regex.Pattern.compile("^(\\d+) (.+)$");

    static CharSequence tr(CharSequence s) {
        if (!ON || s == null || s.length() == 0 || s.length() > 120) return s;
        String[] r = M.get(s.toString());
        if (r != null && r[LANG] != null) return r[LANG];
        String x = s.toString();
        // A count then words ("5 to allow", "3 newsrooms"): translate the words, keep the number.
        java.util.regex.Matcher nm = NUM.matcher(x);
        if (nm.matches()) {
            String[] rr = M.get(nm.group(2));
            if (rr != null && rr[LANG] != null) return nm.group(1) + " " + rr[LANG];
        }
        // Messages built from a fixed beginning and a name ("Closed Netflix"): translate the beginning.
        for (java.util.Map.Entry<String, String[]> e : M.entrySet()) {
            String k = e.getKey();
            if (k.length() > 3 && (k.endsWith(" ") || k.endsWith("\u201C")) && x.startsWith(k) && e.getValue()[LANG] != null) {
                return e.getValue()[LANG] + x.substring(k.length());
            }
        }
        if (x.matches("\\d+ minutes.*")) {
            String[] mn = {"minuti", "minutos", "Minuten", "minutes", "minutos"}, df = {"predefinito", "predeterminado", "Standard", "par d\u00E9faut", "padr\u00E3o"};
            return x.replace(" minutes", " " + mn[LANG]).replace("(default)", "(" + df[LANG] + ")");
        }
        String[] used = {"% in uso", "% usada", "% belegt", "% utilis\u00E9e", "% em uso"};
        if (x.endsWith("% used")) return x.replace("% used", used[LANG]);
        if (x.endsWith("% used\u2026")) return x.replace("% used\u2026", used[LANG] + "\u2026");
        return s;
    }

    /** Translation of a label for code that builds text itself (English when there is none). */
    static String t(String en) {
        return tr(en).toString();
    }

    /** Translated format string with arguments: It.f("%s free", gb). */
    static String f(String en, Object... args) {
        return String.format(Locale.getDefault(), t(en), args);
    }
}
