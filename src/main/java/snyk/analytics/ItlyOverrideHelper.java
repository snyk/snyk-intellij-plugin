package snyk.analytics;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public final class ItlyOverrideHelper {
  public static List<String> convertProducts(List<AnalysisIsReady.AnalysisType> products) {
    if (products == null || products.isEmpty()) {
      return emptyList();
    }

    return products.stream()
                   .map(AnalysisIsReady.AnalysisType::getAnalysisType)
                   .collect(toList());
  }
}
