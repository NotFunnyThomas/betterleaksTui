package tui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TreeElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.toolkit.event.GlobalEventHandler;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.tree.GuideStyle;
import dev.tamboui.widgets.tree.TreeNode;
import dev.tamboui.widgets.tree.TreeWidget;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static dev.tamboui.toolkit.Toolkit.*;

/**
 * Lance Betterleaks via l'image Docker officielle sur le dépôt courant, parse
 * le rapport JSON, puis affiche les findings dans un TUI (arbre fichiers/
 * findings + panneau de détails, avec recherche vim-style {@code /},
 * {@code n}/{@code N}).
 * <p>
 * Betterleaks est le successeur de Gitleaks. Le format JSON reste compatible.
 * <p>
 * Variables d'environnement optionnelles :
 * <ul>
 *   <li>{@code BETTERLEAKS_IMAGE}   : image Docker (défaut {@code ghcr.io/betterleaks/betterleaks:latest}).</li>
 *   <li>{@code BETTERLEAKS_CONFIG}  : chemin (relatif) du fichier de config (défaut {@code .betterleaks.toml}).</li>
 *   <li>{@code BETTERLEAKS_MODE}    : {@code dir} (défaut, filesystem) ou {@code git} (historique).</li>
 *   <li>{@code BETTERLEAKS_STAGED}  : {@code true} pour ajouter {@code --pre-commit --staged} en mode git.</li>
 * </ul>
 */
public class BetterleaksTui {

    public static void main(String[] args) throws Exception {
        Path repoRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        if (!Files.isDirectory(repoRoot)) {
            System.err.println("Repo directory not found: " + repoRoot);
            System.exit(1);
        }

        String image = envOrDefault("BETTERLEAKS_IMAGE", "ghcr.io/betterleaks/betterleaks:latest");
        String configRel = envOrDefault("BETTERLEAKS_CONFIG", ".betterleaks.toml");
        String mode = envOrDefault("BETTERLEAKS_MODE", "dir");
        boolean staged = "true".equalsIgnoreCase(envOrDefault("BETTERLEAKS_STAGED", "false"));

        System.out.println("Running betterleaks (" + image + ", mode=" + mode + ") on " + repoRoot + "...");
        List<Finding> findings;
        try {
            findings = runBetterleaks(repoRoot, image, configRel, mode, staged);
        } catch (Exception e) {
            System.err.println("Failed to run betterleaks: " + e.getMessage());
            System.exit(1);
            return;
        }

        TreeNode<Finding> root = buildTree(findings, repoRoot);

        BetterleaksView view = new BetterleaksView(root, repoRoot, findings.size());
        try (ToolkitRunner runner = ToolkitRunner.builder().build()) {
            GlobalEventHandler searchHandler = event ->
                    event instanceof KeyEvent ke ? view.handleGlobalKey(ke) : EventResult.UNHANDLED;
            runner.eventRouter().addGlobalHandler(searchHandler);
            runner.run(() -> view);
        }
    }

    /** Exécute {@code docker run ... betterleaks <mode> ... --report-format json --report-path ...}. */
    static List<Finding> runBetterleaks(Path repoRoot, String image, String configRel, String mode, boolean staged)
            throws IOException, InterruptedException {
        Path reportDir = Files.createTempDirectory("betterleaks-tui");
        Path reportFile = reportDir.resolve("report.json");

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(repoRoot + ":/repo:ro");
        cmd.add("-v");
        cmd.add(reportDir + ":/out");
        cmd.add("-w");
        cmd.add("/repo");
        cmd.add(image);
        cmd.add(mode);
        cmd.add("/repo");
        cmd.add("--report-format");
        cmd.add("json");
        cmd.add("--report-path");
        cmd.add("/out/report.json");
        cmd.add("--exit-code");
        cmd.add("0"); // n'échoue pas ici; on affiche dans le TUI
        if (Files.isRegularFile(repoRoot.resolve(configRel))) {
            cmd.add("--config");
            cmd.add("/repo/" + configRel);
        }
        if ("git".equalsIgnoreCase(mode) && staged) {
            cmd.add("--pre-commit");
            cmd.add("--staged");
        }

        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("betterleaks timed out");
        }
        int exit = process.exitValue();
        if (exit != 0 && !Files.isRegularFile(reportFile)) {
            throw new IOException("betterleaks exited with " + exit + ":\n" + output);
        }
        return Files.isRegularFile(reportFile) ? parseReport(reportFile) : List.of();
    }

    /** Parse le tableau JSON produit par betterleaks (schéma compatible gitleaks v8+). */
    static List<Finding> parseReport(Path reportFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(reportFile.toFile());
        List<Finding> findings = new ArrayList<>();
        if (root.isArray()) {
            for (JsonNode n : root) {
                findings.add(new Finding(
                        str(n, "RuleID"),
                        str(n, "Description"),
                        str(n, "File"),
                        n.path("StartLine").asInt(-1),
                        n.path("EndLine").asInt(-1),
                        str(n, "Secret"),
                        str(n, "Match"),
                        str(n, "Commit"),
                        str(n, "Author"),
                        str(n, "Email"),
                        str(n, "Date"),
                        str(n, "Message"),
                        toList(n.path("Tags")),
                        n.path("Entropy").asDouble(0.0)));
            }
        }
        return findings;
    }

    private static String str(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? "" : child.asText("");
    }

    private static List<String> toList(JsonNode arrayNode) {
        List<String> out = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode n : arrayNode) {
                out.add(n.asText(""));
            }
        }
        return out;
    }

    private static String envOrDefault(String key, String defaultValue) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? defaultValue : v;
    }

    /** Arbre : racine → fichier → finding. */
    static TreeNode<Finding> buildTree(List<Finding> findings, Path repoRoot) {
        Finding rootMarker = new Finding("", "", repoRoot.toString(), -1, -1, "", "", "", "", "", "", "", List.of(), 0.0);
        TreeNode<Finding> rootNode = TreeNode.of(
                repoRoot.getFileName() == null ? repoRoot.toString() : repoRoot.getFileName().toString(), rootMarker);

        Map<String, List<Finding>> byFile = new LinkedHashMap<>();
        for (Finding f : findings) {
            byFile.computeIfAbsent(f.file(), k -> new ArrayList<>()).add(f);
        }

        List<String> files = new ArrayList<>(byFile.keySet());
        files.sort(Comparator.naturalOrder());
        for (String file : files) {
            List<Finding> group = byFile.get(file);
            Finding fileMarker = new Finding("", "", file, -1, -1, "", "", "", "", "", "", "", List.of(), 0.0);
            TreeNode<Finding> fileNode = TreeNode.of(file + "  (" + group.size() + ")", fileMarker);
            group.sort(Comparator.comparingInt(Finding::startLine));
            for (Finding f : group) {
                TreeNode<Finding> leaf = TreeNode.of(f.ruleId() + " · L" + f.startLine(), f);
                fileNode.add(leaf.leaf());
            }
            rootNode.add(fileNode.expanded());
        }
        return rootNode.expanded();
    }

    /** Un finding betterleaks (uniquement les champs utiles ici). */
    record Finding(
            String ruleId,
            String description,
            String file,
            int startLine,
            int endLine,
            String secret,
            String match,
            String commit,
            String author,
            String email,
            String date,
            String message,
            List<String> tags,
            double entropy) {

        boolean isFileNode() {
            return ruleId.isEmpty() && startLine < 0;
        }
    }

    private static Color severityColor(String ruleId) {
        if (ruleId == null || ruleId.isEmpty()) return Color.WHITE;
        String r = ruleId.toLowerCase(Locale.ROOT);
        if (r.contains("private-key") || r.contains("aws") || r.contains("gcp") || r.contains("azure")) return Color.RED;
        if (r.contains("token") || r.contains("secret") || r.contains("api")) return Color.YELLOW;
        return Color.MAGENTA;
    }

    /** Vue principale : arbre + détails + barre de recherche. */
    static final class BetterleaksView implements Element {

        private final TreeElement<Finding> tree;
        private final Path repoRoot;
        private final int totalFindings;
        private final TextInputState searchState = new TextInputState();
        private boolean searchMode = false;
        private String activeQuery;

        BetterleaksView(TreeNode<Finding> root, Path repoRoot, int totalFindings) {
            this.repoRoot = repoRoot;
            this.totalFindings = totalFindings;
            this.tree = tree(root)
                    .title("Betterleaks findings")
                    .rounded()
                    .highlightColor(Color.CYAN)
                    .scrollbar()
                    .guideStyle(GuideStyle.UNICODE)
                    .nodeRenderer(BetterleaksView::renderNode);
        }

        private static StyledElement<?> renderNode(TreeNode<Finding> node) {
            Finding info = node.data();
            if (info == null || info.isFileNode()) {
                return text(node.label()).bold().cyan().fit();
            }
            List<Element> parts = new ArrayList<>();
            parts.add(text(info.ruleId()).fg(severityColor(info.ruleId())).bold().fit());
            parts.add(text("  L" + info.startLine()).dim().fit());
            if (!info.description().isBlank()) {
                parts.add(text("  " + info.description()).fit());
            }
            parts.add(spacer());
            if (!info.tags().isEmpty()) {
                parts.add(text("[" + String.join(",", info.tags()) + "]").dim().italic().fit());
            }
            parts.add(text(" ").fit());
            return row(parts.toArray(new Element[0]));
        }

        private Element renderDetails(Finding info) {
            if (info == null || info.isFileNode()) {
                return text("(select a finding)").dim();
            }
            List<Element> lines = new ArrayList<>();
            lines.add(row(text("Rule       : ").bold().fit(), text(info.ruleId()).fg(severityColor(info.ruleId())).bold().fit()));
            lines.add(row(text("Description: ").bold().fit(), text(info.description()).fit()));
            lines.add(row(text("File       : ").bold().fit(), text(info.file()).fit()));
            lines.add(row(text("Line       : ").bold().fit(), text(info.startLine() + (info.endLine() > 0 ? "-" + info.endLine() : "")).fit()));
            if (info.entropy() > 0) {
                lines.add(row(text("Entropy    : ").bold().fit(), text(String.format(Locale.ROOT, "%.2f", info.entropy())).fit()));
            }
            if (!info.tags().isEmpty()) {
                lines.add(row(text("Tags       : ").bold().fit(), text(String.join(", ", info.tags())).fit()));
            }
            lines.add(text(""));
            lines.add(text("Match (redacted)").bold().cyan());
            lines.add(text(info.match().isBlank() ? "(no match)" : info.match()).dim());
            if (!info.commit().isBlank()) {
                lines.add(text(""));
                lines.add(text("Commit").bold().cyan());
                lines.add(text("SHA    : " + info.commit()).dim());
                if (!info.author().isBlank()) lines.add(text("Author : " + info.author() + " <" + info.email() + ">").dim());
                if (!info.date().isBlank()) lines.add(text("Date   : " + info.date()).dim());
                if (!info.message().isBlank()) lines.add(text("Message: " + firstLine(info.message())).dim());
            }
            return column(lines.toArray(new Element[0]));
        }

        private static String firstLine(String s) {
            int nl = s.indexOf('\n');
            return nl < 0 ? s : s.substring(0, nl);
        }

        private Element renderSearchBar() {
            return row(
                    text(" / ").bold().yellow().fit(),
                    textInput(searchState)
                            .focusable(false)
                            .cursorRequiresFocus(false)
                            .placeholder("search rule / file / description...")
            ).length(1);
        }

        private Element renderHelpBar() {
            String header = totalFindings == 0
                    ? "No secrets detected in " + repoRoot
                    : totalFindings + " findings in " + repoRoot;
            return row(
                    text(" " + header + "  ").cyan().fit(),
                    text(" [/] Search  [n/N] Next/Prev  [q] Quit ").dim().fit(),
                    spacer(),
                    (activeQuery != null
                            ? text(" Search: \"" + activeQuery + "\" ").cyan()
                            : text("")).fit()
            ).length(1);
        }

        @Override
        public void render(Frame frame, Rect area, RenderContext context) {
            TreeNode<Finding> selectedNode = tree.selectedNode();
            Finding selected = selectedNode != null ? selectedNode.data() : null;

            Element details = panel(
                    column(
                            text("Details").bold().cyan(),
                            text(""),
                            renderDetails(selected)
                    )
            ).title("Details").rounded().borderColor(Color.DARK_GRAY).fill();

            Element ui = column(
                    row(tree.fill(2), details).fill(),
                    searchMode ? renderSearchBar() : renderHelpBar()
            );
            ui.render(frame, area, context);
        }

        @Override
        public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
            return Size.UNKNOWN;
        }

        @Override
        public dev.tamboui.layout.Constraint constraint() {
            return dev.tamboui.layout.Constraint.fill();
        }

        EventResult handleGlobalKey(KeyEvent event) {
            if (searchMode) {
                if (event.isCancel()) {
                    searchMode = false;
                    return EventResult.HANDLED;
                }
                if (event.isConfirm()) {
                    searchMode = false;
                    activeQuery = searchState.text();
                    jumpToMatch(activeQuery, true);
                    return EventResult.HANDLED;
                }
                handleTextInputKey(searchState, event);
                return EventResult.HANDLED;
            }
            if (event.isChar('/')) {
                searchMode = true;
                searchState.clear();
                return EventResult.HANDLED;
            }
            if (activeQuery != null && event.isChar('n')) {
                jumpToMatch(activeQuery, true);
                return EventResult.HANDLED;
            }
            if (activeQuery != null && event.isChar('N')) {
                jumpToMatch(activeQuery, false);
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }

        private void jumpToMatch(String query, boolean forward) {
            if (query == null || query.isBlank()) return;
            String needle = query.toLowerCase(Locale.ROOT);
            List<TreeWidget.FlatEntry<TreeNode<Finding>>> entries = tree.lastFlatEntries();
            int total = entries.size();
            if (total == 0) return;
            int start = tree.selected();
            for (int step = 1; step <= total; step++) {
                int idx = forward ? Math.floorMod(start + step, total) : Math.floorMod(start - step, total);
                Finding info = entries.get(idx).node().data();
                if (info == null || info.isFileNode()) continue;
                String hay = (info.ruleId() + " " + info.description() + " " + info.file()).toLowerCase(Locale.ROOT);
                if (hay.contains(needle)) {
                    tree.selected(idx);
                    return;
                }
            }
        }
    }
}
