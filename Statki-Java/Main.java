import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Collections;

// --- MODEL GRY (LOGIKA BIZNESOWA) ---

// Typ wyliczeniowy reprezentujący precyzyjnie wynik każdego strzału
enum WynikStrzalu { PUDLO, TRAFIENIE, ZATOPIONY, JUZ_STRZELANO}

class Okret {
    private final int dlugosc;
    private int trafienia;

    public Okret(int dlugosc) {
        this.dlugosc = dlugosc;
        this.trafienia = 0;
    }

    public int getDlugosc() { return dlugosc; } 
    public void trafiony() { trafienia++; }
    public boolean czyZatopiony() { return trafienia >= dlugosc; }
}

class Plansza {
    private final int rozmiar = 10;
    private final char[][] siatka;
    private final Okret[][] referencjeOkretow;
    private final List<Okret> flota;

    public Plansza() {
        siatka = new char[rozmiar][rozmiar];
        referencjeOkretow = new Okret[rozmiar][rozmiar];
        flota = new ArrayList<>();
        
        for (int i = 0; i < rozmiar; i++) {
            for (int j = 0; j < rozmiar; j++) {
                siatka[i][j] = '~';
            }
        }
    }

    public char getStanPola(int x, int y) { return siatka[y][x]; }

    public boolean czyWszystkieZatopione() {
        if (flota.isEmpty()) return false;
        for (Okret okret : flota) {
            if (!okret.czyZatopiony()) return false;
        }
        return true;
    }

    public boolean czyMoznaRozmiescic(int dlugosc, int x, int y, boolean poziomo) {
        if (poziomo && x + dlugosc > rozmiar) return false;
        if (!poziomo && y + dlugosc > rozmiar) return false;

        int minX = Math.max(0, x - 1);
        int minY = Math.max(0, y - 1);
        int maxX = Math.min(rozmiar - 1, poziomo ? x + dlugosc : x + 1);
        int maxY = Math.min(rozmiar - 1, poziomo ? y + 1 : y + dlugosc);

        for (int i = minY; i <= maxY; i++) {
            for (int j = minX; j <= maxX; j++) {
                if (siatka[i][j] != '~') return false;
            }
        }
        return true;
    }

    public void rozmiescStatek(Okret okret, int x, int y, boolean poziomo) {
        flota.add(okret);
        for (int i = 0; i < okret.getDlugosc(); i++) {
            int curX = poziomo ? x + i : x;
            int curY = poziomo ? y : y + i;
            siatka[curY][curX] = 'S';
            referencjeOkretow[curY][curX] = okret;
        }
    }

    // Zaktualizowana logika strzału z obsługą nowych wyników i zaznaczaniem kół
    public WynikStrzalu strzal(int x, int y) {
        if (x < 0 || x >= rozmiar || y < 0 || y >= rozmiar) return WynikStrzalu.JUZ_STRZELANO;
        
        char cel = siatka[y][x];
        if (cel == 'X' || cel == 'O') return WynikStrzalu.JUZ_STRZELANO;

        if (cel == 'S') {
            siatka[y][x] = 'X'; // Trafienie
            Okret trafionyOkret = referencjeOkretow[y][x];
            trafionyOkret.trafiony();
            
            if (trafionyOkret.czyZatopiony()) {
                zaznaczPudlaWokolZatopionego(trafionyOkret);
                return WynikStrzalu.ZATOPIONY;
            }
            return WynikStrzalu.TRAFIENIE;
        } else {
            siatka[y][x] = 'O'; // Pudło
            return WynikStrzalu.PUDLO;
        }
    }

    // Metoda hermetyzująca zasady gry: oblewa wodą zatopiony statek
    private void zaznaczPudlaWokolZatopionego(Okret okret) {
        // Przeszukujemy planszę w poszukiwaniu pól tego konkretnego statku
        for (int i = 0; i < rozmiar; i++) {
            for (int j = 0; j < rozmiar; j++) {
                if (referencjeOkretow[i][j] == okret) {
                    // Dla każdego fragmentu statku zaznaczamy pola w promieniu 1 kratki
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int ny = i + dy;
                            int nx = j + dx;
                            // Jeśli mieści się na planszy i jest tam zwykła woda ('~')
                            if (ny >= 0 && ny < rozmiar && nx >= 0 && nx < rozmiar) {
                                if (siatka[ny][nx] == '~') {
                                    siatka[ny][nx] = 'O'; // Ustawiamy wymuszone pudło
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class PrzeciwnikAI {
    private final Random rand = new Random();

    // --- PAMIĘĆ OPERACYJNA BOTA ---
    private Point pierwszeTrafienie = null; // Punkt zahaczenia (pierwszy strzał w statek)
    private Point ostatnieTrafienie = null; // Miejsce ostatniego celnego strzału
    
    // Wektor wyznacza oś i kierunek ataku (np. X=1, Y=0 to atak w prawo)
    private int wektorX = 0;
    private int wektorY = 0;
    
    // Lista sąsiadów do przetestowania po PIERWSZYM trafieniu, by ustalić oś statku
    private List<Point> potencjalneKierunki = new ArrayList<>();

    public void rozstawFlote(Plansza plansza) {
        int[] dlugosciStatkow = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
        for (int dlugosc : dlugosciStatkow) {
            boolean postawiony = false;
            while (!postawiony) {
                int x = rand.nextInt(10);
                int y = rand.nextInt(10);
                boolean poziomo = rand.nextBoolean();
                
                if (plansza.czyMoznaRozmiescic(dlugosc, x, y, poziomo)) {
                    plansza.rozmiescStatek(new Okret(dlugosc), x, y, poziomo);
                    postawiony = true;
                }
            }
        }
    }

    public String wykonajRuch(Plansza planszaGracza) {
        Point strzal = null;
        boolean testujeKierunek = false;

        // 1. ZNALEZIENIE PUNKTU DO STRZAŁU
        if (pierwszeTrafienie != null) {
            
            if (wektorX != 0 || wektorY != 0) {
                // ETAP A: Znamy kierunek statku. Strzelamy dalej w tej linii.
                strzal = new Point(ostatnieTrafienie.x + wektorX, ostatnieTrafienie.y + wektorY);
                
                // Jeśli pole wykracza za planszę lub już tam strzelano (np. oblało wodą inny statek)
                if (!czyPoleWazne(strzal.x, strzal.y, planszaGracza)) {
                    odwrocKierunek(); // Zawracamy na drugi koniec statku!
                    strzal = new Point(ostatnieTrafienie.x + wektorX, ostatnieTrafienie.y + wektorY);
                    
                    // Zabezpieczenie awaryjne (np. gdy zablokują nas inne statki z obu stron)
                    if (!czyPoleWazne(strzal.x, strzal.y, planszaGracza)) {
                        resetujPamiec(); 
                        strzal = null;
                    }
                }
            }

            // ETAP B: Mamy 1 trafienie, ale nie znamy jeszcze kierunku. Badamy sąsiadów.
            if (strzal == null && !potencjalneKierunki.isEmpty()) {
                strzal = potencjalneKierunki.remove(0);
                testujeKierunek = true; // Flaga, że ten strzał posłuży do wyznaczenia wektora
            }
        }

        // ETAP C: Brak zahaczenia – strzelamy losowo "na ślepo"
        if (strzal == null) {
            strzal = generujLosowyStrzal(planszaGracza);
            testujeKierunek = false;
        }

        // 2. WYKONANIE STRZAŁU
        WynikStrzalu wynik = planszaGracza.strzal(strzal.x, strzal.y);

        // Rekurencja w razie awarii (prawie niemożliwa z nową logiką, ale zabezpiecza grę)
        if (wynik == WynikStrzalu.JUZ_STRZELANO) {
            return wykonajRuch(planszaGracza);
        }

        // 3. AKTUALIZACJA WIEDZY (REAKCJA BOTA)
        if (wynik == WynikStrzalu.TRAFIENIE) {
            if (pierwszeTrafienie == null) {
                // Zupełnie nowe trafienie
                pierwszeTrafienie = strzal;
                ostatnieTrafienie = strzal;
                generujSasiadow(strzal, planszaGracza);
            } else {
                // Kolejne trafienie w ten sam statek
                ostatnieTrafienie = strzal;
                if (testujeKierunek) {
                    // Właśnie odkryliśmy w jakiej osi leży statek! Zapisujemy wektor.
                    wektorX = strzal.x - pierwszeTrafienie.x;
                    wektorY = strzal.y - pierwszeTrafienie.y;
                    potencjalneKierunki.clear(); // Reszta sąsiadów (z boku) już nas nie obchodzi
                }
            }
        } else if (wynik == WynikStrzalu.PUDLO) {
            // Jeśli spudłowaliśmy, idąc znanym torem, to znaczy, że statek się skończył z tej strony.
            if (pierwszeTrafienie != null && (wektorX != 0 || wektorY != 0)) {
                odwrocKierunek();
            }
        } else if (wynik == WynikStrzalu.ZATOPIONY) {
            // Statek poszedł na dno. Czyścimy mózg bota z tego celu.
            resetujPamiec();
        }

        // Zwracamy ładny log dla gracza
        char kolumna = (char) ('A' + strzal.x);
        return "Bot: " + kolumna + (strzal.y + 1) + " -> " + wynik;
    }

    // --- METODY POMOCNICZE ---

    // Odwraca atak o 180 stopni i przenosi celownik na początek statku
    private void odwrocKierunek() {
        wektorX = -wektorX;
        wektorY = -wektorY;
        ostatnieTrafienie = pierwszeTrafienie;
    }

    // Czyści pamięć po zatopieniu statku
    private void resetujPamiec() {
        pierwszeTrafienie = null;
        ostatnieTrafienie = null;
        wektorX = 0;
        wektorY = 0;
        potencjalneKierunki.clear();
    }

    // Szuka dostępnych sąsiadów "na krzyż" do sprawdzenia kierunku
    private void generujSasiadow(Point srodek, Plansza plansza) {
        potencjalneKierunki.clear();
        int[][] kierunki = {{0, -1}, {0, 1}, {1, 0}, {-1, 0}};
        
        for (int[] k : kierunki) {
            int nx = srodek.x + k[0];
            int ny = srodek.y + k[1];
            if (czyPoleWazne(nx, ny, plansza)) {
                potencjalneKierunki.add(new Point(nx, ny));
            }
        }
        // Mieszamy kolejność, żeby bot nie atakował zawsze od góry, co uodporni go na czytanie jego ruchów przez Gracza
        Collections.shuffle(potencjalneKierunki);
    }

    // Sprawdza, czy można strzelić w to pole (nie jest poza planszą i jest nieodkryte)
    private boolean czyPoleWazne(int x, int y, Plansza plansza) {
        if (x < 0 || x >= 10 || y < 0 || y >= 10) return false;
        char stan = plansza.getStanPola(x, y);
        return stan == '~' || stan == 'S'; // Woda lub ukryty statek
    }

    private Point generujLosowyStrzal(Plansza plansza) {
        int x, y;
        do {
            x = rand.nextInt(10);
            y = rand.nextInt(10);
        } while (!czyPoleWazne(x, y, plansza));
        return new Point(x, y);
    }
}


// --- WIDOK I KONTROLER (GUI) ---

public class Main extends JFrame {

    enum StanGry { ROZSTAWIANIE, GRA_TRWA, KONIEC_GRY }

    private Plansza planszaGracza;
    private Plansza planszaPrzeciwnika;
    private PrzeciwnikAI bot;
    private StanGry aktualnyStan;

    private final int[] statkiDoRozstawienia = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
    private int indeksRozstawianegoStatku = 0;

    private JLabel statusLabel;
    private JLabel orientacjaLabel;
    private JComboBox<String> orientacjaComboBox;
    
    // Nowe pole dla Historii
    private JTextArea poleHistorii;

    public Main() {
        super("Gra w statki - Wersja Profesjonalna");
        this.planszaGracza = new Plansza();
        this.planszaPrzeciwnika = new Plansza();
        this.bot = new PrzeciwnikAI();
        this.aktualnyStan = StanGry.ROZSTAWIANIE;
        
        bot.rozstawFlote(planszaPrzeciwnika);

        inicjalizujGUI();
    }

    private void inicjalizujGUI() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLayout(new BorderLayout());
        this.setResizable(false);

        // --- GÓRNY PANEL ---
        JPanel panelGornu = new JPanel();
        panelGornu.setBackground(new Color(200, 220, 240));
        statusLabel = new JLabel("Rozstaw swój statek: " + statkiDoRozstawienia[0] + "-masztowiec. Kliknij na swoją planszę.");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        orientacjaLabel = new JLabel("  Orientacja: ");
        orientacjaComboBox = new JComboBox<>(new String[]{"W poziomie", "W pionie"});
        
        panelGornu.add(statusLabel);
        panelGornu.add(orientacjaLabel);
        panelGornu.add(orientacjaComboBox);
        this.add(panelGornu, BorderLayout.NORTH);

        // --- PRAWY PANEL (HISTORIA RUCHÓW) ---
        JPanel panelHistorii = new JPanel(new BorderLayout());
        panelHistorii.setPreferredSize(new Dimension(250, 0));
        panelHistorii.setBorder(BorderFactory.createTitledBorder("Historia ruchów"));
        
        poleHistorii = new JTextArea();
        poleHistorii.setEditable(false); // Blokujemy możliwość wpisywania tekstu przez gracza
        poleHistorii.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        // JScrollPane dodaje pasek przewijania do pola tekstowego
        JScrollPane scrollPane = new JScrollPane(poleHistorii);
        panelHistorii.add(scrollPane, BorderLayout.CENTER);
        this.add(panelHistorii, BorderLayout.EAST);

        // --- ŚRODKOWY PANEL (PŁÓTNO GRY) ---
        GraWidok plotnoGry = new GraWidok();
        this.add(plotnoGry, BorderLayout.CENTER);

        this.pack();
        this.setLocationRelativeTo(null);
    }
    
    // Metoda pomocnicza do dodawania logów i automatycznego przewijania na sam dół
    private void dodajLog(String tekst) {
        poleHistorii.append(tekst + "\n");
        // Przewiń kursor na sam koniec dokumentu
        poleHistorii.setCaretPosition(poleHistorii.getDocument().getLength());
    }

    // Wewnętrzna klasa reprezentująca płótno do malowania i obsługę kliknięć
    private class GraWidok extends JPanel {
        private final int rozmiarKratki = 35;
        private final int startXGracz = 40;
        private final int startXPrzeciwnik = 460;
        private final int startY = 80;

        public GraWidok() {
            this.setPreferredSize(new Dimension(860, 500));
            this.setBackground(new Color(240, 248, 255));
            
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (aktualnyStan == StanGry.KONIEC_GRY) return;
                    int myszX = e.getX();
                    int myszY = e.getY();

                    if (aktualnyStan == StanGry.ROZSTAWIANIE) {
                        obsluzKlikniecieRozstawiania(myszX, myszY);
                    } else if (aktualnyStan == StanGry.GRA_TRWA) {
                        obsluzKlikniecieStrzalu(myszX, myszY);
                    }
                }
            });
        }

        private void obsluzKlikniecieRozstawiania(int myszX, int myszY) {
            if (myszX >= startXGracz && myszX < startXGracz + (10 * rozmiarKratki) &&
                myszY >= startY && myszY < startY + (10 * rozmiarKratki)) {
                
                int x = (myszX - startXGracz) / rozmiarKratki;
                int y = (myszY - startY) / rozmiarKratki;
                
                int dlugosc = statkiDoRozstawienia[indeksRozstawianegoStatku];
                boolean poziomo = orientacjaComboBox.getSelectedIndex() == 0;

                if (planszaGracza.czyMoznaRozmiescic(dlugosc, x, y, poziomo)) {
                    planszaGracza.rozmiescStatek(new Okret(dlugosc), x, y, poziomo);
                    indeksRozstawianegoStatku++;
                    
                    if (indeksRozstawianegoStatku < statkiDoRozstawienia.length) {
                        statusLabel.setText("Rozstaw swój statek: " + statkiDoRozstawienia[indeksRozstawianegoStatku] + "-masztowiec.");
                    } else {
                        aktualnyStan = StanGry.GRA_TRWA;
                        
                        // Bezpieczne ukrywanie obu elementów interfejsu
                        orientacjaComboBox.setVisible(false);
                        orientacjaLabel.setVisible(false);
                        
                        // Odświeżenie rodzica (JFrame/JPanel), aby przeliczył układ bez tych elementów
                        orientacjaLabel.getParent().revalidate();
                        orientacjaLabel.getParent().repaint();
                        
                        statusLabel.setText("GRA TRWA! Twój ruch. Kliknij na planszę przeciwnika.");
                        statusLabel.setForeground(Color.RED);
                        dodajLog("--- BITWA ROZPOCZĘTA ---");
                    }
                    // Odświeżenie samej planszy
                    repaint();
                }
            }
        }

        private void obsluzKlikniecieStrzalu(int myszX, int myszY) {
            if (myszX >= startXPrzeciwnik && myszX < startXPrzeciwnik + (10 * rozmiarKratki) &&
                myszY >= startY && myszY < startY + (10 * rozmiarKratki)) {
                
                int x = (myszX - startXPrzeciwnik) / rozmiarKratki;
                int y = (myszY - startY) / rozmiarKratki;

                // TURA GRACZA
                WynikStrzalu wynikGracza = planszaPrzeciwnika.strzal(x, y);
                if (wynikGracza == WynikStrzalu.JUZ_STRZELANO) return; // Ignorujemy puste kliknięcia

                char kolumna = (char) ('A' + x);
                dodajLog("Gracz: " + kolumna + (y + 1) + " -> " + wynikGracza);

                if (planszaPrzeciwnika.czyWszystkieZatopione()) {
                    zakonczGre("WYGRAŁEŚ!");
                    repaint();
                    return;
                }

                // TURA BOTA
                String logBota = bot.wykonajRuch(planszaGracza);
                dodajLog(logBota);
                
                if (planszaGracza.czyWszystkieZatopione()) {
                    zakonczGre("PRZEGRAŁEŚ! Przeciwnik zatopił Twoją flotę.");
                }
                repaint();
            }
        }

        private void zakonczGre(String wiadomosc) {
            aktualnyStan = StanGry.KONIEC_GRY;
            statusLabel.setText(wiadomosc);
            dodajLog("\n" + wiadomosc);
            JOptionPane.showMessageDialog(Main.this, wiadomosc, "Koniec Gry", JOptionPane.INFORMATION_MESSAGE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            rysujPlansze(g, planszaGracza, startXGracz, startY, "Twoja plansza", true);
            boolean pokazCaleStatki = (aktualnyStan == StanGry.KONIEC_GRY); 
            rysujPlansze(g, planszaPrzeciwnika, startXPrzeciwnik, startY, "Plansza przeciwnika", pokazCaleStatki);
        }

        private void rysujPlansze(Graphics g, Plansza plansza, int ox, int oy, String tytul, boolean pokazZimneStatki) {
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Arial", Font.BOLD, 14));
            g.drawString(tytul, ox + (10 * rozmiarKratki)/2 - 50, oy - 25);

            for (int i = 0; i < 10; i++) {
                g.setColor(Color.BLACK);
                g.drawString(String.valueOf((char)('A' + i)), ox + (i * rozmiarKratki) + rozmiarKratki/2 - 5, oy - 5);
                g.drawString(String.valueOf(i + 1), ox - 25, oy + (i * rozmiarKratki) + rozmiarKratki/2 + 5);

                for (int j = 0; j < 10; j++) {
                    int rectX = ox + (j * rozmiarKratki);
                    int rectY = oy + (i * rozmiarKratki);
                    
                    g.setColor(new Color(173, 216, 230));
                    g.fillRect(rectX, rectY, rozmiarKratki, rozmiarKratki);
                    g.setColor(Color.BLUE);
                    g.drawRect(rectX, rectY, rozmiarKratki, rozmiarKratki);

                    char stanPola = plansza.getStanPola(j, i);

                    if (stanPola == 'S' && pokazZimneStatki) {
                        g.setColor(Color.GRAY);
                        g.fillRect(rectX + 2, rectY + 2, rozmiarKratki - 4, rozmiarKratki - 4);
                    } else if (stanPola == 'X') {
                        g2d.setStroke(new BasicStroke(3));
                        g.setColor(Color.RED);
                        g.drawLine(rectX + 5, rectY + 5, rectX + rozmiarKratki - 5, rectY + rozmiarKratki - 5);
                        g.drawLine(rectX + rozmiarKratki - 5, rectY + 5, rectX + 5, rectY + rozmiarKratki - 5);
                        g2d.setStroke(new BasicStroke(1));
                    } else if (stanPola == 'O') {
                        g2d.setStroke(new BasicStroke(3));
                        g.setColor(Color.BLACK);
                        g.drawOval(rectX + 8, rectY + 8, rozmiarKratki - 16, rozmiarKratki - 16);
                        g2d.setStroke(new BasicStroke(1));
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Main().setVisible(true);
        });
    }
}