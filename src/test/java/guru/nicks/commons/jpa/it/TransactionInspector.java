package guru.nicks.commons.jpa.it;

import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records transaction state from inside repository calls. Since fragment implementations are plain classes
 * instantiated per repository (not Spring beans), the state is captured via JPA lifecycle callbacks on
 * {@code TestDocument}: they run inside whatever transaction the repository proxy established for the call.
 */
public final class TransactionInspector {

    private static final List<Snapshot> snapshots = new CopyOnWriteArrayList<>();

    private TransactionInspector() {
    }

    /**
     * Clears previously recorded snapshots; call right before invoking a repository method under inspection.
     */
    public static void startRecording() {
        snapshots.clear();
    }

    /**
     * Records current transaction state; invoked from JPA lifecycle callbacks.
     *
     * @param event lifecycle callback name, e.g. 'load' or 'persist'
     */
    public static void capture(String event) {
        snapshots.add(new Snapshot(event,
                TransactionSynchronizationManager.isActualTransactionActive(),
                TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                currentTransactionIdentity()));
    }

    /**
     * Returns snapshots recorded since the last {@link #startRecording()}.
     *
     * @return recorded snapshots, never {@code null}
     */
    public static List<Snapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    /**
     * Returns identity hash of the EntityManager holder bound to the current thread, or -1 when no transaction is
     * active. Two captures with the same identity ran inside the same transaction.
     *
     * @return identity hash of the bound EntityManager holder, or -1
     */
    private static int currentTransactionIdentity() {
        return TransactionSynchronizationManager.getResourceMap().values().stream()
                .filter(EntityManagerHolder.class::isInstance)
                .mapToInt(System::identityHashCode)
                .findFirst()
                .orElse(-1);
    }

    /**
     * Transaction state observed inside a repository call.
     *
     * @param event lifecycle callback that captured the state
     * @param transactionActive whether a transaction was active at capture time
     * @param readOnly whether the active transaction was read-only
     * @param transactionIdentity identity hash of the bound EntityManager holder, or -1 if none
     */
    public record Snapshot(String event, boolean transactionActive, boolean readOnly, int transactionIdentity) {
    }

}
