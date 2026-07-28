package pt.servimatch.platform.appversion;

/**
 * Comparação mínima de versões semânticas ({@code major.minor.patch}) —
 * suficiente para decidir {@code OK}/{@code UPDATE_RECOMMENDED}/
 * {@code UPDATE_REQUIRED}; ignora deliberadamente pré-lançamentos/metadados
 * de build (não usados nas versões publicadas da app).
 */
record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    static SemVer parse(String value) {
        String core = value.split("[-+]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            throw new IllegalArgumentException("Versão inválida: " + value);
        }
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new SemVer(major, minor, patch);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Versão inválida: " + value, e);
        }
    }

    @Override
    public int compareTo(SemVer other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }
}
