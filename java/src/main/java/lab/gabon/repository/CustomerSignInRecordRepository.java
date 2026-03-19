package lab.gabon.repository;

import java.util.Optional;
import lab.gabon.model.entity.CustomerSignInRecord;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface CustomerSignInRecordRepository extends CrudRepository<CustomerSignInRecord, Long> {

  @Query(
      """
      SELECT * FROM customer_sign_in_records
      WHERE customer_id = :customerId AND period_key = :periodKey
      """)
  Optional<CustomerSignInRecord> findByCustomerAndPeriodKey(long customerId, String periodKey);
}
