/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Requires(env = "hibernate")
@Tag(name = "hibernate-demo")
@Controller("/hibernate-demo/{sessionFactory}")
public class HibernateDemoController {

    private static final Argument<SessionFactory> SESSION_FACTORY_ARGUMENT = Argument.of(SessionFactory.class);
    private static final int DEFAULT_STATISTICS_RUNS = 4;
    private static final int MAX_STATISTICS_RUNS = 100;
    private static final List<DemoAuthor> DEMO_LIBRARY = List.of(
        new DemoAuthor("Toni Morrison", "US", List.of(
            new DemoBook("Beloved Hibernate", "978-1-0001-0001-1", 324, 2018),
            new DemoBook("Micronaut Persistence Notes", "978-1-0001-0002-8", 212, 2020),
            new DemoBook("Second-Level Cache Stories", "978-1-0001-0007-3", 276, 2024)
        )),
        new DemoAuthor("Ursula Le Guin", "US", List.of(
            new DemoBook("Queries of Earthsea", "978-1-0001-0003-5", 288, 2019),
            new DemoBook("The Left Hand of SQL", "978-1-0001-0004-2", 304, 2021),
            new DemoBook("A Wizard of Fetch Plans", "978-1-0001-0008-0", 264, 2022)
        )),
        new DemoAuthor("Douglas Adams", "UK", List.of(
            new DemoBook("The Hibernate Guide", "978-1-0001-0005-9", 248, 2022),
            new DemoBook("Mostly Harmless Joins", "978-1-0001-0006-6", 196, 2023),
            new DemoBook("Life, the Universe, and JDBC", "978-1-0001-0009-7", 236, 2024)
        )),
        new DemoAuthor("Octavia Butler", "US", List.of(
            new DemoBook("Parable of the Persistence Context", "978-1-0001-0010-3", 328, 2021),
            new DemoBook("Kindred Entity Graphs", "978-1-0001-0011-0", 292, 2023),
            new DemoBook("Patternmaster Queries", "978-1-0001-0012-7", 264, 2024)
        )),
        new DemoAuthor("Mary Shelley", "UK", List.of(
            new DemoBook("Frankenstein's Session Factory", "978-1-0001-0013-4", 312, 2020),
            new DemoBook("The Modern Prometheus ORM", "978-1-0001-0014-1", 284, 2022),
            new DemoBook("A Last Man's Transaction", "978-1-0001-0015-8", 352, 2024)
        )),
        new DemoAuthor("N. K. Jemisin", "US", List.of(
            new DemoBook("The Fifth Normal Form", "978-1-0001-0016-5", 316, 2020),
            new DemoBook("The Obelisk Query", "978-1-0001-0017-2", 336, 2022),
            new DemoBook("The Stone Sky Schema", "978-1-0001-0018-9", 348, 2024)
        ))
    );

    private final Map<String, SessionFactory> sessionFactories;
    private final Set<String> initializedSessionFactories = ConcurrentHashMap.newKeySet();

    public HibernateDemoController(BeanLocator locator) {
        this.sessionFactories = locator.mapOfType(SESSION_FACTORY_ARGUMENT);
    }

    @Operation(summary = "List Hibernate demo books")
    @Get("/books")
    public List<Map<String, Object>> books(@PathVariable String sessionFactory) {
        return withSession(requireSessionFactory(sessionFactory), session -> {
            seed(session);
            return session.createQuery("select b from HibernateBook b order by b.title", HibernateBook.class)
                .getResultList()
                .stream()
                .map(HibernateDemoController::bookSummary)
                .toList();
        });
    }

    @Operation(summary = "Fetch one Hibernate demo book")
    @Get("/books/{id}")
    public Map<String, Object> book(@PathVariable String sessionFactory, @PathVariable Long id) {
        return withSession(requireSessionFactory(sessionFactory), session -> {
            seed(session);
            var book = session.find(HibernateBook.class, id);
            if (book == null) {
                return orderedMap("found", false);
            }
            var result = bookSummary(book);
            result.put("found", true);
            return result;
        });
    }

    @Operation(summary = "List Hibernate demo authors and books")
    @Get("/authors")
    public List<Map<String, Object>> authors(@PathVariable String sessionFactory) {
        return withSession(requireSessionFactory(sessionFactory), session -> {
            seed(session);
            return session.createQuery("select distinct a from HibernateAuthor a left join fetch a.books order by a.name", HibernateAuthor.class)
                .getResultList()
                .stream()
                .map(HibernateDemoController::authorSummary)
                .toList();
        });
    }

    @Operation(summary = "Search Hibernate demo books")
    @Get("/search{?term}")
    public List<Map<String, Object>> search(@PathVariable String sessionFactory, @QueryValue Optional<String> term) {
        return withSession(requireSessionFactory(sessionFactory), session -> {
            seed(session);
            var queryTerm = "%" + term.orElse("hibernate").toLowerCase(Locale.ROOT) + "%";
            return session.createQuery("""
                    select b
                    from HibernateBook b
                    where lower(b.title) like :term or lower(b.isbn) like :term
                    order by b.title
                    """, HibernateBook.class)
                .setParameter("term", queryTerm)
                .getResultList()
                .stream()
                .map(HibernateDemoController::bookSummary)
                .toList();
        });
    }

    @Operation(summary = "Run Hibernate queries to populate statistics")
    @Get("/statistics/fill{?runs}")
    public Map<String, Object> fillStatistics(@PathVariable String sessionFactory, @QueryValue Optional<Integer> runs) {
        var selectedSessionFactory = requireSessionFactory(sessionFactory);
        var runCount = Math.max(1, Math.min(MAX_STATISTICS_RUNS, runs.orElse(DEFAULT_STATISTICS_RUNS)));
        var statistics = selectedSessionFactory.getStatistics();
        var statisticsEnabledBeforeRun = statistics.isStatisticsEnabled();
        if (!statisticsEnabledBeforeRun) {
            statistics.setStatisticsEnabled(true);
        }
        withSession(selectedSessionFactory, session -> {
            seed(session);
            return null;
        });
        var cacheWarmup = warmCache(selectedSessionFactory);

        var totals = new StatisticsRunSummary(0, 0, 0, 0, 0);
        for (int i = 0; i < runCount; i++) {
            totals = totals.plus(runStatisticsBatch(selectedSessionFactory, i));
        }
        var updatedRows = updateOneBook(selectedSessionFactory);

        var result = orderedMap("runs", runCount);
        result.put("sessionFactory", sessionFactory);
        result.put("hibernateSessionFactory", sessionFactory);
        result.put("datasource", sessionFactory);
        result.put("statisticsEnabled", statistics.isStatisticsEnabled());
        result.put("statisticsEnabledBeforeRun", statisticsEnabledBeforeRun);
        result.put("statisticsEnabledByEndpoint", !statisticsEnabledBeforeRun);
        result.put("queryTypes", List.of(
            "count books",
            "cacheable recent books",
            "filtered book search",
            "cacheable author list",
            "second-level entity cache",
            "second-level collection cache",
            "author and book cache warm-up",
            "aggregate books by country",
            "Native SQL book count",
            "Native SQL book list",
            "Native SQL aggregate by country"
        ));
        result.put("cacheWarmupAuthors", cacheWarmup.authorsRead());
        result.put("cacheWarmupBooks", cacheWarmup.booksRead());
        result.put("cacheWarmupCollectionItems", cacheWarmup.collectionItemsRead());
        result.put("rowsRead", totals.rowsRead());
        result.put("aggregateRows", totals.aggregateRows());
        result.put("collectionItemsRead", totals.collectionItemsRead());
        result.put("entityLoads", totals.entityLoads());
        result.put("nativeRowsRead", totals.nativeRowsRead());
        result.put("rowsUpdated", updatedRows);
        return result;
    }

    private CacheWarmupSummary warmCache(SessionFactory sessionFactory) {
        var firstPass = withSession(sessionFactory, session -> {
            var authorIds = session.createQuery("""
                    select a.id
                    from HibernateAuthor a
                    order by a.id
                    """, Long.class)
                .getResultList();
            var bookIds = session.createQuery("""
                    select b.id
                    from HibernateBook b
                    order by b.id
                    """, Long.class)
                .getResultList();

            return new CacheWarmupPass(authorIds, bookIds, accessAuthorBookGraph(session, authorIds, bookIds));
        });
        var secondPass = withSession(sessionFactory, session -> accessAuthorBookGraph(session, firstPass.authorIds(), firstPass.bookIds()));
        return firstPass.summary().plus(secondPass);
    }

    private static CacheWarmupSummary accessAuthorBookGraph(Session session, List<Long> authorIds, List<Long> bookIds) {
        var authorsRead = 0;
        var booksRead = 0;
        var collectionItemsRead = 0;
        for (Long authorId : authorIds) {
            var author = session.find(HibernateAuthor.class, authorId);
            if (author != null) {
                author.getName();
                author.getCountry();
                collectionItemsRead += author.getBooks().size();
                authorsRead++;
            }
        }
        for (Long bookId : bookIds) {
            var book = session.find(HibernateBook.class, bookId);
            if (book != null) {
                book.getTitle();
                book.getIsbn();
                if (book.getAuthor() != null) {
                    book.getAuthor().getName();
                }
                booksRead++;
            }
        }
        return new CacheWarmupSummary(authorsRead, booksRead, collectionItemsRead);
    }

    private StatisticsRunSummary runStatisticsBatch(SessionFactory sessionFactory, int index) {
        var queryCacheEnabled = isQueryCacheEnabled(sessionFactory);
        return withSession(sessionFactory, session -> {
            var rowsRead = session.createQuery("select count(b) from HibernateBook b", Long.class)
                .getSingleResult()
                .intValue();

            var recentBooksQuery = session.createQuery("""
                    select b
                    from HibernateBook b
                    where b.publishedYear >= :year
                    order by b.publishedYear desc, b.title
                    """, HibernateBook.class)
                .setParameter("year", 2018)
                .setMaxResults(5);
            cacheable(recentBooksQuery, queryCacheEnabled, "hibernateDemo.recentBooks");
            var recentBooks = recentBooksQuery.getResultList();
            rowsRead += recentBooks.size();

            var searchTerm = index % 2 == 0 ? "%hibernate%" : "%micronaut%";
            rowsRead += session.createQuery("""
                    select b
                    from HibernateBook b
                    join b.author a
                    where lower(b.title) like :term or lower(a.name) like :term
                    order by a.name, b.title
                    """, HibernateBook.class)
                .setParameter("term", searchTerm)
                .getResultList()
                .size();

            var authorsQuery = session.createQuery("""
                    select a
                    from HibernateAuthor a
                    order by a.name
                    """, HibernateAuthor.class);
            cacheable(authorsQuery, queryCacheEnabled, "hibernateDemo.authors");
            var authors = authorsQuery
                .getResultList();
            var collectionItemsRead = authors.stream()
                .mapToInt(author -> author.getBooks().size())
                .sum();

            var bookIdsQuery = session.createQuery("""
                    select b.id
                    from HibernateBook b
                    order by b.id
                    """, Long.class);
            cacheable(bookIdsQuery, queryCacheEnabled, "hibernateDemo.bookIds");
            var bookIds = bookIdsQuery
                .getResultList();
            var entityLoads = 0;
            for (Long bookId : bookIds) {
                var book = session.find(HibernateBook.class, bookId);
                if (book != null) {
                    book.getTitle();
                    if (book.getAuthor() != null) {
                        book.getAuthor().getName();
                    }
                    entityLoads++;
                }
            }
            rowsRead += entityLoads;

            var countrySummaryQuery = session.createQuery("""
                    select a.country, count(b), avg(b.pages)
                    from HibernateAuthor a
                    join a.books b
                    group by a.country
                    order by a.country
                    """, Object[].class);
            cacheable(countrySummaryQuery, queryCacheEnabled, "hibernateDemo.countrySummary");
            var aggregateRows = countrySummaryQuery
                .getResultList()
                .size();

            var nativeRowsRead = nativeRowsRead(session);

            return new StatisticsRunSummary(rowsRead, aggregateRows, collectionItemsRead, entityLoads, nativeRowsRead);
        });
    }

    private static int nativeRowsRead(Session session) {
        var rowsRead = ((Number) session.createNativeQuery("""
                select count(*)
                from hibernatebook
                """)
            .getSingleResult()).intValue();
        rowsRead += session.createNativeQuery("""
                select b.title, b.pages
                from hibernatebook b
                where b.pages >= :pages
                order by b.title
                """)
            .setParameter("pages", 250)
            .setMaxResults(6)
            .getResultList()
            .size();
        rowsRead += session.createNativeQuery("""
                select a.country, count(b.id), avg(b.pages)
                from hibernateauthor a
                join hibernatebook b on b.author_id = a.id
                group by a.country
                order by a.country
                """)
            .getResultList()
            .size();
        return rowsRead;
    }

    private int updateOneBook(SessionFactory sessionFactory) {
        return withSession(sessionFactory, session -> {
            var book = session.createQuery("""
                    select b
                    from HibernateBook b
                    order by b.id
                    """, HibernateBook.class)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
            if (book == null) {
                return 0;
            }
            book.setPages(book.getPages() + 1);
            return 1;
        });
    }

    private SessionFactory requireSessionFactory(String sessionFactory) {
        var selectedSessionFactory = sessionFactories.get(sessionFactory);
        if (selectedSessionFactory == null) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "Hibernate session factory not found: " + sessionFactory);
        }
        ensureDemoSchema(sessionFactory, selectedSessionFactory);
        return selectedSessionFactory;
    }

    private void ensureDemoSchema(String name, SessionFactory sessionFactory) {
        if (initializedSessionFactories.contains(name)) {
            return;
        }
        synchronized (initializedSessionFactories) {
            if (initializedSessionFactories.contains(name)) {
                return;
            }
            try {
                withSession(sessionFactory, session -> {
                    countAuthors(session);
                    return null;
                });
            } catch (RuntimeException e) {
                if (!isMissingDemoTable(e)) {
                    throw e;
                }
                sessionFactory.getSchemaManager().exportMappedObjects(false);
            }
            initializedSessionFactories.add(name);
        }
    }

    private static <T> T withSession(SessionFactory sessionFactory, Function<Session, T> work) {
        Transaction transaction = null;
        try (Session session = sessionFactory.openSession()) {
            transaction = session.beginTransaction();
            var result = work.apply(session);
            transaction.commit();
            return result;
        } catch (RuntimeException e) {
            if (transaction != null) {
                rollback(transaction, e);
            }
            throw e;
        }
    }

    private static boolean isQueryCacheEnabled(SessionFactory sessionFactory) {
        var options = sessionFactory.getSessionFactoryOptions();
        return options != null && options.isQueryCacheEnabled();
    }

    private static void cacheable(Query<?> query, boolean queryCacheEnabled, String cacheRegion) {
        if (queryCacheEnabled) {
            query.setCacheable(true);
            query.setCacheRegion(cacheRegion);
        }
    }

    private static void rollback(Transaction transaction, RuntimeException failure) {
        try {
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } catch (RuntimeException rollbackException) {
            failure.addSuppressed(rollbackException);
        }
    }

    private static void seed(Session session) {
        for (DemoAuthor demoAuthor : DEMO_LIBRARY) {
            var author = findAuthorByName(session, demoAuthor.name());
            if (author == null) {
                author = new HibernateAuthor();
                author.setName(demoAuthor.name());
                author.setCountry(demoAuthor.country());
                session.persist(author);
            }
            for (DemoBook demoBook : demoAuthor.books()) {
                if (!bookExists(session, demoBook.isbn())) {
                    var book = book(demoBook);
                    author.addBook(book);
                    session.persist(book);
                }
            }
        }
        session.flush();
    }

    private static HibernateAuthor findAuthorByName(Session session, String name) {
        return session.createQuery("""
                select a
                from HibernateAuthor a
                where a.name = :name
                """, HibernateAuthor.class)
            .setParameter("name", name)
            .setMaxResults(1)
            .getResultList()
            .stream()
            .findFirst()
            .orElse(null);
    }

    private static boolean bookExists(Session session, String isbn) {
        return session.createQuery("""
                select count(b)
                from HibernateBook b
                where b.isbn = :isbn
                """, Long.class)
            .setParameter("isbn", isbn)
            .getSingleResult() > 0;
    }

    private static HibernateBook book(DemoBook demoBook) {
        return book(demoBook.title(), demoBook.isbn(), demoBook.pages(), demoBook.publishedYear());
    }

    private static HibernateBook book(String title, String isbn, int pages, int publishedYear) {
        var book = new HibernateBook();
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setPages(pages);
        book.setPublishedYear(publishedYear);
        return book;
    }

    private static Map<String, Object> authorSummary(HibernateAuthor author) {
        var result = orderedMap("id", author.getId());
        result.put("name", author.getName());
        result.put("country", author.getCountry());
        result.put("books", author.getBooks().stream()
            .map(HibernateDemoController::bookSummary)
            .toList());
        return result;
    }

    private static Map<String, Object> bookSummary(HibernateBook book) {
        var result = orderedMap("id", book.getId());
        result.put("title", book.getTitle());
        result.put("isbn", book.getIsbn());
        result.put("pages", book.getPages());
        result.put("publishedYear", book.getPublishedYear());
        result.put("author", book.getAuthor() == null ? "" : book.getAuthor().getName());
        return result;
    }

    private static Map<String, Object> orderedMap(String key, Object value) {
        var result = new LinkedHashMap<String, Object>();
        result.put(key, value);
        return result;
    }

    private record StatisticsRunSummary(int rowsRead, int aggregateRows, int collectionItemsRead, int entityLoads, int nativeRowsRead) {
        StatisticsRunSummary plus(StatisticsRunSummary other) {
            return new StatisticsRunSummary(
                rowsRead + other.rowsRead,
                aggregateRows + other.aggregateRows,
                collectionItemsRead + other.collectionItemsRead,
                entityLoads + other.entityLoads,
                nativeRowsRead + other.nativeRowsRead
            );
        }
    }

    private record CacheWarmupSummary(int authorsRead, int booksRead, int collectionItemsRead) {
        CacheWarmupSummary plus(CacheWarmupSummary other) {
            return new CacheWarmupSummary(
                authorsRead + other.authorsRead,
                booksRead + other.booksRead,
                collectionItemsRead + other.collectionItemsRead
            );
        }
    }

    private record CacheWarmupPass(List<Long> authorIds, List<Long> bookIds, CacheWarmupSummary summary) {
    }

    private record DemoAuthor(String name, String country, List<DemoBook> books) {
    }

    private record DemoBook(String title, String isbn, int pages, int publishedYear) {
    }

    private static long countAuthors(Session session) {
        return session.createQuery("select count(a) from HibernateAuthor a", Long.class)
            .getSingleResult();
    }

    private static boolean isMissingDemoTable(RuntimeException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && "42P01".equals(sqlException.getSQLState())) {
                return true;
            }
            var message = cause.getMessage();
            if (message != null) {
                var normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("hibernateauthor") && normalizedMessage.contains("does not exist")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
