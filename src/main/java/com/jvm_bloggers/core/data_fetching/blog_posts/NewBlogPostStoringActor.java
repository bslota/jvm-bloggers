package com.jvm_bloggers.core.data_fetching.blog_posts;

import akka.actor.AbstractActor;
import akka.actor.Props;
import com.jvm_bloggers.entities.blog_post.BlogPost;
import com.jvm_bloggers.entities.blog_post.BlogPostRepository;
import com.jvm_bloggers.utils.DateTimeUtilities;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.jvm_bloggers.core.utils.Validators.isUrlValid;
import static org.apache.commons.lang3.StringUtils.abbreviate;

@Slf4j
@RequiredArgsConstructor
public class NewBlogPostStoringActor extends AbstractActor {

    private static final String HTTPS_PREFIX = "https";
    private static final String HTTP_PREFIX = "http";

    private final BlogPostRepository blogPostRepository;
    private final BlogPostFactory blogPostFactory;

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(RssEntryWithAuthor.class,
            rssEntry -> {
                String blogPostLink = rssEntry.getRssEntry().getLink();
                if (isUrlValid(blogPostLink)) {
                    BlogPost blogPost = blogPostRepository
                            .findByUrlEndingWith(removeProtocolFrom(blogPostLink))
                            .getOrElse(() -> createBlogPost(rssEntry));
                    updateDescription(blogPost, rssEntry.getRssEntry().getDescription());
                    blogPostRepository.save(blogPost);
                } else {
                    log.info(
                            "Detected blog post with invalid link {}. Skipping DB operation",
                        blogPostLink
                    );
                }
            }).build();
    }

    public static Props props(BlogPostRepository blogPostRepository,
                              BlogPostFactory blogPostFactory) {
        return Props.create(
            NewBlogPostStoringActor.class,
            () -> new NewBlogPostStoringActor(blogPostRepository, blogPostFactory)
        );
    }

    private String removeProtocolFrom(String link) {
        return link
            .replaceAll("^" + HTTPS_PREFIX, "")
            .replaceAll("^" + HTTP_PREFIX, "");
    }

    private BlogPost createBlogPost(RssEntryWithAuthor rssEntry) {
        SyndEntry postInRss = rssEntry.getRssEntry();
        Date dateToStore = firstNonNull(postInRss.getPublishedDate(), postInRss.getUpdatedDate());
        log.info("Creating new post '{}' by {}", postInRss.getTitle(),
            rssEntry.getBlog().getAuthor());
        return blogPostFactory.create(
            postInRss.getTitle(),
            postInRss.getLink(),
            DateTimeUtilities.toLocalDateTime(dateToStore),
            rssEntry.getBlog());
    }

    private void updateDescription(BlogPost blogPost, SyndContent descriptionContent) {
        if (descriptionContent != null) {
            String description = descriptionContent.getValue();
            description = abbreviate(description, BlogPost.MAX_DESCRIPTION_LENGTH);
            blogPost.setDescription(description);
        }
    }

}
