package uz.mittisoftware.mittigo.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Mitti GO admin panel: a small web app on top of the server's admin API (BACKEND_MVP §6.7). It keeps no data of its
 * own — the admin token lives only in the server-side session, never in the browser.
 */
@SpringBootApplication
public class MittiGoAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MittiGoAdminApplication.class, args);
    }
}
