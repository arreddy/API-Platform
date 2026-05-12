package com.apiplatform.controlplane.converter;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidStringConverterTest {

  private final UuidStringConverter converter = new UuidStringConverter();

  @Test
  void convertToDatabaseColumn_validUuid_returnsUuid() {
    String id = UUID.randomUUID().toString();
    assertThat(converter.convertToDatabaseColumn(id)).isEqualTo(UUID.fromString(id));
  }

  @Test
  void convertToDatabaseColumn_null_returnsNull() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
  }

  @Test
  void convertToDatabaseColumn_blank_returnsNull() {
    assertThat(converter.convertToDatabaseColumn("   ")).isNull();
  }

  @Test
  void convertToDatabaseColumn_emptyString_returnsNull() {
    assertThat(converter.convertToDatabaseColumn("")).isNull();
  }

  @Test
  void convertToDatabaseColumn_invalidUuid_throwsIllegalArgument() {
    assertThatThrownBy(() -> converter.convertToDatabaseColumn("not-a-uuid"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void convertToEntityAttribute_uuid_returnsString() {
    UUID uuid = UUID.randomUUID();
    assertThat(converter.convertToEntityAttribute(uuid)).isEqualTo(uuid.toString());
  }

  @Test
  void convertToEntityAttribute_null_returnsNull() {
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }

  @Test
  void roundTrip_preservesValue() {
    String original = UUID.randomUUID().toString();
    UUID db = converter.convertToDatabaseColumn(original);
    String back = converter.convertToEntityAttribute(db);
    assertThat(back).isEqualTo(original);
  }
}
