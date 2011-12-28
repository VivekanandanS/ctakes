package edu.mayo.bmi.uima.lookup.ae;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import org.apache.uima.analysis_engine.annotator.AnnotatorContext;
import org.apache.uima.analysis_engine.annotator.AnnotatorProcessException;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.jcas.JCas;

import edu.mayo.bmi.dictionary.MetaDataHit;
import edu.mayo.bmi.lookup.vo.LookupHit;
import edu.mayo.bmi.uima.SmokingStatus.type.SmokerNamedEntityAnnotation;
import edu.mayo.bmi.uima.core.type.OntologyConcept;
import edu.mayo.bmi.uima.core.util.TypeSystemConst;
import edu.mayo.bmi.uima.lookup.ae.BaseLookupConsumerImpl;
import edu.mayo.bmi.uima.lookup.ae.LookupConsumer;

/**
 * copied from edu.may.bmi.uima.lookup.ae.NamedEntityLookupConsumerImpl in the "Dictionary Lookup" project
 * to use "SmokerNamedEntityAnnotation" instead of NamedEntityAnnotation
 */
public class SmokerNamedEntityLookupConsumerImpl extends BaseLookupConsumerImpl
		implements LookupConsumer
{

	private final String CODE_MF_PRP_KEY = "codeMetaField";

	private final String CODING_SCHEME_PRP_KEY = "codingScheme";

	private Properties iv_props;

	public SmokerNamedEntityLookupConsumerImpl(AnnotatorContext aCtx, Properties props)
	{
		// TODO property validation
		iv_props = props;
	}

	public void consumeHits(JCas jcas, Iterator lhItr)
			throws AnnotatorProcessException
	{
		Iterator hitsByOffsetItr = organizeByOffset(lhItr);
		while (hitsByOffsetItr.hasNext())
		{
			Collection hitsAtOffsetCol = (Collection) hitsByOffsetItr.next();

			FSArray ocArr = new FSArray(jcas, hitsAtOffsetCol.size());
			int ocArrIdx = 0;

			// iterate over the LookupHit objects and create
			// a corresponding JCas OntologyConcept object that will
			// be placed in a FSArray
			Iterator lhAtOffsetItr = hitsAtOffsetCol.iterator();
			int neBegin = -1;
			int neEnd = -1;
			while (lhAtOffsetItr.hasNext())
			{
				LookupHit lh = (LookupHit) lhAtOffsetItr.next();
				neBegin = lh.getStartOffset();
				neEnd = lh.getEndOffset();
				//MetaDataHit mdh = lh.getGazMetaDataHit();
				MetaDataHit mdh = lh.getDictMetaDataHit();

				OntologyConcept oc = new OntologyConcept(jcas);
				oc.setCode(mdh.getMetaFieldValue(iv_props.getProperty(CODE_MF_PRP_KEY)));
				oc.setCodingScheme(iv_props.getProperty(CODING_SCHEME_PRP_KEY));

				ocArr.set(ocArrIdx, oc);
				ocArrIdx++;
			}

			SmokerNamedEntityAnnotation neAnnot = new SmokerNamedEntityAnnotation(jcas); //modification
			neAnnot.setBegin(neBegin);
			neAnnot.setEnd(neEnd);
			neAnnot.setDiscoveryTechnique(TypeSystemConst.NE_DISCOVERY_TECH_DICT_LOOKUP);
			neAnnot.setOntologyConceptArr(ocArr);
			neAnnot.addToIndexes();
		}
	}
}
