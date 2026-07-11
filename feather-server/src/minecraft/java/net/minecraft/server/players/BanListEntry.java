package net.minecraft.server.players;

import com.google.gson.JsonObject;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public abstract class BanListEntry<T> extends StoredUserEntry<T> {
    // Leaf start - Use faster and thread-safe ban list date format parsing
    //public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT); // Leaf - I assume no one will use this, if yes, why?
    private static final java.time.ZoneId ZONE_ID = java.time.ZoneId.systemDefault();
    public static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    // Leaf end - Use faster and thread-safe ban list date format parsing
    public static final String EXPIRES_NEVER = "forever";
    protected final Date created;
    protected final String source;
    protected final @Nullable Date expires;
    protected final @Nullable String reason;

    public BanListEntry(
        final @Nullable T user, final @Nullable Date created, final @Nullable String source, final @Nullable Date expires, final @Nullable String reason
    ) {
        super(user);
        this.created = created == null ? new Date() : created;
        this.source = source == null ? "(Unknown)" : source;
        this.expires = expires;
        this.reason = reason;
    }

    protected BanListEntry(final @Nullable T user, final JsonObject object) {
        super(BanListEntry.checkExpiry(user, object)); // CraftBukkit

        Date created;
        try {
            // Leaf start - Use faster and thread-safe ban list date format parsing
            created = object.has("created") ? parseToDate(object.get("created").getAsString()) : new Date();
        } catch (java.time.format.DateTimeParseException ignored) {
            // Leaf end - Use faster and thread-safe ban list date format parsing
            created = new Date();
        }

        this.created = created;
        this.source = object.has("source") ? object.get("source").getAsString() : "(Unknown)";

        Date expires;
        try {
            // Leaf start - Use faster and thread-safe ban list date format parsing
            expires = object.has("expires") ? parseToDate(object.get("expires").getAsString()) : null;
        } catch (java.time.format.DateTimeParseException ignored) {
            // Leaf end - Use faster and thread-safe ban list date format parsing
            expires = null;
        }

        this.expires = expires;
        this.reason = object.has("reason") ? object.get("reason").getAsString() : null;
    }

    public Date getCreated() {
        return this.created;
    }

    public String getSource() {
        return this.source;
    }

    public @Nullable Date getExpires() {
        return this.expires;
    }

    public @Nullable String getReason() {
        return this.reason;
    }

    public Component getReasonMessage() {
        String reason = this.getReason();
        return reason == null ? Component.translatable("multiplayer.disconnect.banned.reason.default") : Component.literal(reason);
    }

    public abstract Component getDisplayName();

    @Override
    public boolean hasExpired() {
        return this.expires != null && this.expires.before(new Date());
    }

    @Override
    protected void serialize(final JsonObject object) {
        object.addProperty("created", formateToString(this.created)); // Leaf - Use faster and thread-safe ban list date format parsing
        object.addProperty("source", this.source);
        object.addProperty("expires", this.expires == null ? "forever" : formateToString(this.expires)); // Leaf - Use faster and thread-safe ban list date format parsing
        object.addProperty("reason", this.reason);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            BanListEntry<?> that = (BanListEntry<?>)o;
            return Objects.equals(this.source, that.source)
                && Objects.equals(this.expires, that.expires)
                && Objects.equals(this.reason, that.reason)
                && Objects.equals(this.getUser(), that.getUser());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.source, this.expires, this.reason, this.getUser());
    }

    // CraftBukkit start
    private static <T> T checkExpiry(T object, JsonObject jsonobject) {
        Date expires = null;

        try {
            // Leaf start - Use faster and thread-safe ban list date format parsing
            expires = jsonobject.has("expires") ? parseToDate(jsonobject.get("expires").getAsString()) : null;
        } catch (java.time.format.DateTimeParseException ex) {
            // Guess we don't have a date
            // Leaf end - Use faster and thread-safe ban list date format parsing
        }

        if (expires == null || expires.after(new Date())) {
            return object;
        } else {
            return null;
        }
    }
    // CraftBukkit end

    // Leaf start - Use faster and thread-safe ban list date format parsing
    public static Date parseToDate(final String string) {
        java.time.ZonedDateTime parsedDateTime = java.time.ZonedDateTime.parse(string, DATE_TIME_FORMATTER);
        return Date.from(parsedDateTime.toInstant());
    }

    private static String formateToString(final Date date) {
        return DATE_TIME_FORMATTER.format(date.toInstant().atZone(ZONE_ID));
    }
    // Leaf end - Use faster and thread-safe ban list date format parsing
}
