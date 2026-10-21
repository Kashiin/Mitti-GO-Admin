package uz.mittisoftware.mittigo.admin.web;

import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uz.mittisoftware.mittigo.admin.backend.BackendException;

/** Runs one change against the server and leaves a success or error line for the next page. */
final class Flash {

    private Flash() {
    }

    static void run(RedirectAttributes flash, String success, Runnable change) {
        run(flash, () -> {
            change.run();
            return success;
        });
    }

    /** [change] returns the success line itself — e.g. one that names what the server just changed. */
    static void run(RedirectAttributes flash, Supplier<String> change) {
        try {
            flash.addFlashAttribute("success", change.get());
        } catch (BackendException e) {
            if (e.isUnauthorized()) throw e;

            flash.addFlashAttribute("error", e.userMessage());
        }
    }
}
