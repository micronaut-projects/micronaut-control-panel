package example;

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.Cacheable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

@Entity
@NamedQueries({
    @NamedQuery(
        name = "HibernateBook.listTitles",
        query = "select b.title, b.publishedYear from HibernateBook b order by b.title"
    ),
    @NamedQuery(
        name = "HibernateBook.recentBooks",
        query = "from HibernateBook b where b.publishedYear >= 2020 order by b.publishedYear desc, b.title"
    )
})
@NamedNativeQuery(
    name = "HibernateBook.nativeBookSummary",
    query = "select title, pages from hibernatebook order by title"
)
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Introspected
public class HibernateBook {

    @Id
    @GeneratedValue
    private Long id;

    private String title;

    private String isbn;

    private int pages;

    private int publishedYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private HibernateAuthor author;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(int publishedYear) {
        this.publishedYear = publishedYear;
    }

    public HibernateAuthor getAuthor() {
        return author;
    }

    public void setAuthor(HibernateAuthor author) {
        this.author = author;
    }
}
