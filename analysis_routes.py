from datetime import timezone
from bson import ObjectId
from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    Request,
)
from sqlalchemy.ext.asyncio import AsyncSession
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.models import AnalysisHistoryResponse, FactorAnalysisResponse, User
from app.dependencies import logger
from app.database import get_db, get_mongo_db
from app.crud.users import get_user
from app.crud.analyses import get_analysis, get_history
from app.crud.mongo.analyses import get_analysis_mongo, get_history_mongo
from app.middleware.cookie_verification import cookie_verification

router = APIRouter()


@router.get("/analysis/{user_id}", response_model=AnalysisHistoryResponse)
async def history_handler(
    request: Request,
    user_id: str,
    email: str = Depends(cookie_verification),
    db_session: AsyncSession = Depends(get_db),
    mongo_db: AsyncIOMotorDatabase = Depends(get_mongo_db),
):
    history_data = []
    try:
        mongo_history_data = await get_history_mongo(mongo_db, user_id)
        history_data += mongo_history_data

        if len(history_data) < 5:
            pg_history_data = await get_history(db_session, user_id, 5-len(history_data))
            history_data += pg_history_data

        for item in history_data:
            item["created_at"] = item["created_at"].replace(tzinfo=timezone.utc)
    except Exception as e:
        raise HTTPException(status_code=503, detail=str(e))
    
    return {"analysis_history": history_data}


@router.get("/analysis/{user_id}/{analysis_id}", response_model=FactorAnalysisResponse)
async def get_analysis_handler(
    request: Request,
    user_id: str,
    analysis_id: str,
    email: str = Depends(cookie_verification),
    db_session: AsyncSession = Depends(get_db),
    mongo_db: AsyncIOMotorDatabase = Depends(get_mongo_db),
):
    data = await get_analysis(db_session, user_id, analysis_id)
    if not data:
        raise HTTPException(status_code=404, detail="Whoops! Looks like we couldn't find that analysis.")
    result = []
    factor_name, analysis, file_name, created_at, feedback = data
    result.append(
        {
            "factor_name": factor_name,
            "analysis": analysis,
            "file_name": file_name,
            "created_at": created_at,
            "feedback": feedback,
        }
    )
    return result
