package it.ninjatech.apt.example;

public final class ModelJournalMonitor {

    private static ModelJournalMonitor instance;

    public static ModelJournalMonitor getInstance() {
        return ModelJournalMonitor.instance == null
                                                    ? ModelJournalMonitor.instance = new ModelJournalMonitor()
                                                    : ModelJournalMonitor.instance;
    }
    
    private ModelJournalMonitor() {}

    public void notifyUpdate(AbstractModel model) {}
    
}
