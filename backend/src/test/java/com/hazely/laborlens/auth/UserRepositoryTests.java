package com.hazely.laborlens.auth;

import com.hazely.laborlens.entities.User;
import com.hazely.laborlens.repositories.UserRepository;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Apply V2 after V1 and validate the user mapping without touching the local application database. */
@Testcontainers(disabledWithoutDocker = true)
class UserRepositoryTests {
    @Container static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");
    @Test
    void migratesPreservesDataAndPersistsRevocation() {
        var ds = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("1").load().migrate();
        var sql = new JdbcTemplate(ds);
        sql.update("INSERT INTO annual_employment_rate (year,age_group,sex,education_attainment_level,nuts_2_region,employment_rate_percent) "
                + "VALUES (2025,'ALL','ALL','ALL','IRELAND',75)");
        var migration = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertEquals(1, migration.migrate().migrationsExecuted);
        migration.validate();
        assertEquals(1, sql.queryForObject("SELECT COUNT(*) FROM annual_employment_rate", Integer.class));
        var config = new Configuration().addAnnotatedClass(User.class)
                .setProperty("hibernate.connection.url", mysql.getJdbcUrl())
                .setProperty("hibernate.connection.username", mysql.getUsername())
                .setProperty("hibernate.connection.password", mysql.getPassword())
                .setProperty("hibernate.hbm2ddl.auto", "validate");
        try (var factory = config.buildSessionFactory(); var em = factory.createEntityManager()) {
            var repo = new JpaRepositoryFactory(em).getRepository(UserRepository.class);
            em.getTransaction().begin();
            var user = new User("person@example.test", "Same name", new BCryptPasswordEncoder(4).encode("password123"));
            em.persist(user);
            em.persist(new User("other@example.test", "Same name", user.getPasswordHash()));
            em.getTransaction().commit();
            em.clear();
            assertTrue(repo.existsByEmail("PERSON@EXAMPLE.TEST"));
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> sql.update(
                    "INSERT INTO users (email,username,password_hash,token_version,created_at) VALUES (?,?,?,0,NOW())",
                    "PERSON@EXAMPLE.TEST", "Name", user.getPasswordHash()));
            em.getTransaction().begin();
            var locked = repo.lockEmail("person@example.test").orElseThrow();
            locked.revoke();
            em.getTransaction().commit();
            em.clear();
            assertEquals(1, repo.findById(user.getId()).orElseThrow().getTokenVersion());
            em.getTransaction().begin();
            em.remove(repo.lockId(user.getId()).orElseThrow());
            em.getTransaction().commit();
            assertTrue(repo.findById(user.getId()).isEmpty());
        }
    }
}
